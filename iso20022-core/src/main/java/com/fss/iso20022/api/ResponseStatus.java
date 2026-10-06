package com.fss.iso20022.api;

/**
 * High-level status of the ISO 20022 message validation.
 */
public enum ResponseStatus {
    /**
     * The message is structurally and semantically valid (or valid with warnings).
     */
    VALID,

    /**
     * The message failed structural or semantic validation and is rejected.
     */
    REJECTED,

    /**
     * The system encountered an unrecoverable technical error processing the message
     * (e.g. missing JAXB model, semantic engine crash, malformed XML).
     */
    SYSTEM_ERROR
}
