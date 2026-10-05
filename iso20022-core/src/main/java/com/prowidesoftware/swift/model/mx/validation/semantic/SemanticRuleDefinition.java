package com.prowidesoftware.swift.model.mx.validation.semantic;

import java.util.List;

public class SemanticRuleDefinition {
    private String ruleId;
    private String messageType;
    private List<String> versions;
    private String severity;
    private String expression;
    private String errorPath;
    private String message;

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public List<String> getVersions() {
        return versions;
    }

    public void setVersions(List<String> versions) {
        this.versions = versions;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public String getErrorPath() {
        return errorPath;
    }

    public void setErrorPath(String errorPath) {
        this.errorPath = errorPath;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
