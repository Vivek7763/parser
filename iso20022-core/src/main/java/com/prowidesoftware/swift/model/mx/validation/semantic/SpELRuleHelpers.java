package com.prowidesoftware.swift.model.mx.validation.semantic;

import java.math.BigDecimal;
import java.util.Collection;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

public class SpELRuleHelpers {

    public static int parseInt(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Cannot parse empty string to int");
        }
        return Integer.parseInt(value);
    }

    public static int getCurrencyDecimals(String currency) {
        if (currency == null) {
            throw new IllegalArgumentException("Currency cannot be null");
        }
        switch (currency.toUpperCase()) {
            case "USD":
            case "EUR":
            case "GBP":
                return 2;
            case "JPY":
                return 0;
            case "BHD":
            case "KWD":
                return 3;
            default:
                throw new IllegalArgumentException("Unknown currency: " + currency);
        }
    }

    public static BigDecimal sum(Collection<?> items, String property) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }
        if (property == null || property.isBlank()) {
            throw new IllegalArgumentException("Property cannot be null or empty");
        }

        // Security requirement: Implement a safe property-path resolver rather than parsing arbitrary SpEL
        // Use SimpleEvaluationContext for read-only property navigation to avoid sandbox bypass
        SimpleEvaluationContext safeContext =
                SimpleEvaluationContext.forReadOnlyDataBinding().build();
        ExpressionParser parser = new SpelExpressionParser();
        Expression exp = parser.parseExpression(property);

        BigDecimal total = BigDecimal.ZERO;
        for (Object item : items) {
            Object val = exp.getValue(safeContext, item);
            if (val instanceof Number) {
                total = total.add(new BigDecimal(val.toString()));
            } else if (val instanceof String) {
                total = total.add(new BigDecimal((String) val));
            } else if (val != null) {
                throw new IllegalArgumentException(
                        "Property " + property + " is not a number. Found: " + val.getClass());
            }
        }
        return total;
    }
}
