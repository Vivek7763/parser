package com.prowidesoftware.swift.model.mx.validation.semantic;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

public class SpELRuleEvaluator implements RuleEvaluator {
    private final ExpressionParser parser = new SpelExpressionParser();

    // Security: SimpleEvaluationContext explicitly blocks arbitrary method invocation,
    // reflection, class instantiation, and restricts access strictly to getters.
    private final SimpleEvaluationContext context =
            SimpleEvaluationContext.forReadOnlyDataBinding().build();

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
