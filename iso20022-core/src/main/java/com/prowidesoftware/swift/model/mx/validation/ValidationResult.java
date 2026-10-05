package com.prowidesoftware.swift.model.mx.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ValidationResult {
    private final String messageId;
    private final List<ValidationError> errors = new ArrayList<>();

    public ValidationResult(String messageId) {
        this.messageId = messageId;
    }

    public void addError(ValidationError error) {
        this.errors.add(error);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public String getMessageId() {
        return messageId;
    }

    public List<ValidationError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ValidationResult [messageId=")
                .append(messageId)
                .append(", isValid=")
                .append(isValid())
                .append("]\n");
        for (ValidationError e : errors) {
            sb.append("  ").append(e.toString()).append("\n");
        }
        return sb.toString();
    }
}
