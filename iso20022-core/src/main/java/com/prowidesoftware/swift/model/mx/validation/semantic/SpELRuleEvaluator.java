package com.prowidesoftware.swift.model.mx.validation.semantic;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import java.util.Collection;
import java.util.List;
import javax.xml.datatype.DatatypeFactory;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.expression.*;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.expression.spel.support.StandardTypeComparator;
import org.springframework.expression.spel.support.StandardTypeConverter;

public class SpELRuleEvaluator implements RuleEvaluator {
    private final ExpressionParser parser = new SpelExpressionParser();
    private final EvaluationContext context;

    public SpELRuleEvaluator() {
        DefaultConversionService conversionService = new DefaultConversionService();

        // Add explicit type conversion for Prowide Date/Time types
        // Prowide models expose java.time types (e.g. OffsetDateTime, LocalDate) instead of XMLGregorianCalendar
        // This allows SpEL string literals (e.g. '2023-10-01T12:00:00Z') to be compared against model fields natively.
        conversionService.addConverter(
                String.class, java.time.OffsetDateTime.class, source -> java.time.OffsetDateTime.parse(source));
        conversionService.addConverter(
                String.class, java.time.LocalDate.class, source -> java.time.LocalDate.parse(source));
        conversionService.addConverter(
                String.class, java.time.LocalTime.class, source -> java.time.LocalTime.parse(source));
        conversionService.addConverter(
                String.class, java.time.OffsetTime.class, source -> java.time.OffsetTime.parse(source));
        conversionService.addConverter(
                String.class, java.time.YearMonth.class, source -> java.time.YearMonth.parse(source));
        conversionService.addConverter(String.class, java.time.Year.class, source -> java.time.Year.parse(source));

        // Fallback for legacy XMLGregorianCalendar if any older schema uses it
        conversionService.addConverter(String.class, javax.xml.datatype.XMLGregorianCalendar.class, source -> {
            try {
                return DatatypeFactory.newInstance().newXMLGregorianCalendar(source);
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot parse XMLGregorianCalendar: " + source, e);
            }
        });

        // Security: SimpleEvaluationContext explicitly blocks arbitrary method invocation,
        // reflection, class instantiation, and restricts access strictly to getters.
        SimpleEvaluationContext delegate = SimpleEvaluationContext.forReadOnlyDataBinding()
                .withTypeConverter(new StandardTypeConverter(conversionService))
                .build();

        // SpEL TypeComparator natively delegates to Comparable, but fails ClassCastException if types differ (e.g.
        // OffsetDateTime vs String)
        // We inject a TypeComparator that intercepts String literals compared against Prowide Date types and converts
        // the String first.
        TypeComparator customComparator = new TypeComparator() {
            private final StandardTypeComparator std = new StandardTypeComparator();

            @Override
            public boolean canCompare(Object left, Object right) {
                if (left != null
                        && right instanceof String
                        && (left.getClass().getName().startsWith("java.time.")
                                || left instanceof javax.xml.datatype.XMLGregorianCalendar)) {
                    return true;
                }
                return std.canCompare(left, right);
            }

            @Override
            public int compare(Object left, Object right) throws EvaluationException {
                if (left != null
                        && right instanceof String
                        && (left.getClass().getName().startsWith("java.time.")
                                || left instanceof javax.xml.datatype.XMLGregorianCalendar)) {
                    Object converted = conversionService.convert(right, left.getClass());
                    return std.compare(left, converted);
                }
                return std.compare(left, right);
            }
        };

        // Wrap the strictly secured SimpleEvaluationContext to inject the custom TypeComparator
        this.context = new EvaluationContext() {
            @Override
            public TypedValue getRootObject() {
                return delegate.getRootObject();
            }

            @Override
            public List<PropertyAccessor> getPropertyAccessors() {
                return delegate.getPropertyAccessors();
            }

            @Override
            public List<ConstructorResolver> getConstructorResolvers() {
                return delegate.getConstructorResolvers();
            }

            @Override
            public List<MethodResolver> getMethodResolvers() {
                return delegate.getMethodResolvers();
            }

            @Override
            public BeanResolver getBeanResolver() {
                return delegate.getBeanResolver();
            }

            @Override
            public TypeLocator getTypeLocator() {
                return delegate.getTypeLocator();
            }

            @Override
            public TypeConverter getTypeConverter() {
                return delegate.getTypeConverter();
            }

            @Override
            public TypeComparator getTypeComparator() {
                return customComparator;
            }

            @Override
            public OperatorOverloader getOperatorOverloader() {
                return delegate.getOperatorOverloader();
            }

            @Override
            public void setVariable(String name, Object value) {
                delegate.setVariable(name, value);
            }

            @Override
            public Object lookupVariable(String name) {
                return delegate.lookupVariable(name);
            }
        };

        try {
            this.context.setVariable("parseInt", SpELRuleHelpers.class.getDeclaredMethod("parseInt", String.class));
            this.context.setVariable(
                    "getCurrencyDecimals",
                    SpELRuleHelpers.class.getDeclaredMethod("getCurrencyDecimals", String.class));
            this.context.setVariable(
                    "sum", SpELRuleHelpers.class.getDeclaredMethod("sum", Collection.class, String.class));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Failed to register SpEL helpers", e);
        }
    }

    @Override
    public boolean evaluate(SemanticRuleDefinition rule, AbstractMX model) {
        try {
            Expression exp = parser.parseExpression(rule.getExpression());
            Boolean result = exp.getValue(context, model, Boolean.class);
            return result != null && result;
        } catch (Exception e) {
            // Throw exception to indicate a faulty or malicious rule configuration
            throw new IllegalArgumentException("SpEL Evaluation failed or blocked: " + e.getMessage(), e);
        }
    }
}
