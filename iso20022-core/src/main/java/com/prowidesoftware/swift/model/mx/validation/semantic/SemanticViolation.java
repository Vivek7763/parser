package com.prowidesoftware.swift.model.mx.validation.semantic;

public class SemanticViolation {
    private final String ruleId;
    private final String severity;
    private final String message;
    private final String errorPath;

    public SemanticViolation(String ruleId, String severity, String message, String errorPath) {
        this.ruleId = ruleId;
        this.severity = severity;
        this.message = message;
        this.errorPath = errorPath;
    }

    public String getRuleId() {
        return ruleId;
    }

    public String getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public String getErrorPath() {
        return errorPath;
    }
}
