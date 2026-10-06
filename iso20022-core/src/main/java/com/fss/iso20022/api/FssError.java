package com.fss.iso20022.api;

import java.util.Objects;

/**
 * Unified representation of a validation error or warning.
 * This class abstracts away internal parser representations (like SAX ValidationErrors or SpEL SemanticViolations).
 */
public final class FssError {
    private final ErrorCategory category;
    private final String code;
    private final String message;
    private final String location;
    private final String severity;

    public FssError(ErrorCategory category, String code, String message, String location, String severity) {
        this.category = Objects.requireNonNull(category, "category must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.message = Objects.requireNonNull(message, "message must not be null");
        this.location = location; // can be null if location is unknown or global
        this.severity = Objects.requireNonNull(severity, "severity must not be null");
    }

    public ErrorCategory getCategory() {
        return category;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getLocation() {
        return location;
    }

    public String getSeverity() {
        return severity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FssError fssError = (FssError) o;
        return category == fssError.category
                && code.equals(fssError.code)
                && message.equals(fssError.message)
                && Objects.equals(location, fssError.location)
                && severity.equals(fssError.severity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(category, code, message, location, severity);
    }

    @Override
    public String toString() {
        return "FssError{" + "category="
                + category + ", code='"
                + code + '\'' + ", message='"
                + message + '\'' + ", location='"
                + location + '\'' + ", severity='"
                + severity + '\'' + '}';
    }
}
