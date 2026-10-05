package com.prowidesoftware.swift.model.mx.validation.semantic;

import java.util.ArrayList;
import java.util.List;

public class SemanticValidationResult {
    private final List<SemanticViolation> violations = new ArrayList<>();
    private final List<TechnicalEvaluationError> technicalErrors = new ArrayList<>();
    private int evaluatedRuleCount = 0;

    public void incrementEvaluatedRuleCount() {
        this.evaluatedRuleCount++;
    }

    public int getEvaluatedRuleCount() {
        return evaluatedRuleCount;
    }

    public void addViolation(SemanticViolation violation) {
        this.violations.add(violation);
    }

    public void addTechnicalError(TechnicalEvaluationError error) {
        this.technicalErrors.add(error);
    }

    public boolean hasFailures() {
        return !violations.isEmpty() || !technicalErrors.isEmpty();
    }

    public List<SemanticViolation> getViolations() {
        return java.util.Collections.unmodifiableList(violations);
    }

    public List<TechnicalEvaluationError> getTechnicalErrors() {
        return java.util.Collections.unmodifiableList(technicalErrors);
    }
}
