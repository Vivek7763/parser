package com.prowidesoftware.swift.model.mx.validation.semantic;

public class TechnicalEvaluationError {
    private final String ruleId;
    private final String errorMessage;

    public TechnicalEvaluationError(String ruleId, String errorMessage) {
        this.ruleId = ruleId;
        this.errorMessage = errorMessage;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
