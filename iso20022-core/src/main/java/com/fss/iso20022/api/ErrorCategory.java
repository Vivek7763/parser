package com.fss.iso20022.api;

/**
 * Category of a validation error.
 */
public enum ErrorCategory {
    /**
     * XML structural constraints, schema violations, invalid data types.
     */
    STRUCTURAL,

    /**
     * Business logic rules, cross-field checks.
     */
    SEMANTIC,

    /**
     * Parsing failures, unidentifiable namespaces, missing schemas/models, or internal crashes.
     */
    TECHNICAL
}
