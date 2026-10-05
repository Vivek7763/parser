package com.prowidesoftware.swift.model.mx.validation.semantic;

import com.prowidesoftware.swift.model.mx.AbstractMX;

public interface RuleEvaluator {
    boolean evaluate(SemanticRuleDefinition rule, AbstractMX model);
}
