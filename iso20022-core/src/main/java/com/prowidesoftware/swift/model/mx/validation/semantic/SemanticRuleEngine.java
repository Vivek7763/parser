package com.prowidesoftware.swift.model.mx.validation.semantic;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import java.util.ArrayList;
import java.util.List;

public class SemanticRuleEngine {
    private final List<SemanticRuleDefinition> rules = new ArrayList<>();
    private final RuleEvaluator evaluator;

    public SemanticRuleEngine(RuleEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    public void addRule(SemanticRuleDefinition rule) {
        if (rule.getRuleId() == null || rule.getRuleId().isEmpty())
            throw new IllegalArgumentException("ruleId is required");
        if (rule.getMessageType() == null || rule.getMessageType().isEmpty())
            throw new IllegalArgumentException("messageType is required");
        if (rule.getExpression() == null || rule.getExpression().isEmpty())
            throw new IllegalArgumentException("expression is required");
        if (rule.getSeverity() == null || rule.getSeverity().isEmpty())
            throw new IllegalArgumentException("severity is required");
        if (rule.getErrorPath() == null || rule.getErrorPath().isEmpty())
            throw new IllegalArgumentException("errorPath is required");
        rules.add(rule);
    }

    public SemanticValidationResult validate(AbstractMX model) {
        SemanticValidationResult result = new SemanticValidationResult();

        String modelProcess = model.getBusinessProcess();
        String modelVariant = String.format("%03d", model.getFunctionality()) + "."
                + String.format("%03d", model.getVariant()) + "."
                + String.format("%02d", model.getVersion());
        String modelId = modelProcess + "." + modelVariant;

        for (SemanticRuleDefinition rule : rules) {
            // Rule Applicability
            if (rule.getMessageType() != null && !modelId.startsWith(rule.getMessageType())) {
                continue;
            }
            if (rule.getVersions() != null && !rule.getVersions().isEmpty()) {
                if (!rule.getVersions().contains(String.valueOf(model.getVersion()))) {
                    continue;
                }
            }

            try {
                result.incrementEvaluatedRuleCount();
                boolean passed = evaluator.evaluate(rule, model);
                if (!passed) {
                    result.addViolation(new SemanticViolation(
                            rule.getRuleId(), rule.getSeverity(), rule.getMessage(), rule.getErrorPath()));
                }
            } catch (Exception e) {
                result.addTechnicalError(
                        new TechnicalEvaluationError(rule.getRuleId(), "Rule Evaluation Error: " + e.getMessage()));
            }
        }
        return result;
    }
}
