package com.prowidesoftware.swift.model.mx.validation.semantic;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SemanticRuleLoader {
    private static final Gson GSON = new Gson();

    /**
     * Loads rules from an InputStream (JSON array format).
     * Validates required fields fail-fast.
     */
    public static List<SemanticRuleDefinition> loadJsonRules(InputStream is) {
        if (is == null) {
            throw new IllegalArgumentException("InputStream cannot be null");
        }
        try (Reader reader = new InputStreamReader(is, "UTF-8")) {
            List<SemanticRuleDefinition> rules =
                    GSON.fromJson(reader, new TypeToken<List<SemanticRuleDefinition>>() {}.getType());
            if (rules == null) {
                return Collections.emptyList();
            }

            // Validate and enforce fail-fast structure
            for (SemanticRuleDefinition rule : rules) {
                if (rule.getRuleId() == null || rule.getRuleId().trim().isEmpty()) {
                    throw new IllegalArgumentException("ruleId is required in JSON rule");
                }
                if (rule.getMessageType() == null
                        || rule.getMessageType().trim().isEmpty()) {
                    throw new IllegalArgumentException(
                            "messageType is required in JSON rule [" + rule.getRuleId() + "]");
                }
                if (rule.getExpression() == null || rule.getExpression().trim().isEmpty()) {
                    throw new IllegalArgumentException(
                            "expression is required in JSON rule [" + rule.getRuleId() + "]");
                }
                if (rule.getSeverity() == null || rule.getSeverity().trim().isEmpty()) {
                    throw new IllegalArgumentException("severity is required in JSON rule [" + rule.getRuleId() + "]");
                }
                if (rule.getErrorPath() == null || rule.getErrorPath().trim().isEmpty()) {
                    throw new IllegalArgumentException("errorPath is required in JSON rule [" + rule.getRuleId() + "]");
                }
            }

            return Collections.unmodifiableList(new ArrayList<>(rules));
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new RuntimeException("Failed to load semantic rules from JSON", e);
        }
    }
}
