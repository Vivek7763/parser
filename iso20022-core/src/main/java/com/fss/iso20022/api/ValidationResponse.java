package com.fss.iso20022.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The public-facing result of ISO 20022 message validation.
 * This DTO is completely decoupled from Prowide and internal orchestration details.
 */
public final class ValidationResponse {
    private final String messageId;
    private final ResponseStatus status;
    private final List<FssError> errors;

    public ValidationResponse(String messageId, ResponseStatus status, List<FssError> errors) {
        // messageId can be null if the input was completely unidentifiable
        this.messageId = messageId;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.errors = errors != null ? Collections.unmodifiableList(new ArrayList<>(errors)) : Collections.emptyList();
    }

    /**
     * Returns the full message type identifier (e.g., "pacs.002.001.12"),
     * or null if the message type could not be determined.
     */
    public String getMessageId() {
        return messageId;
    }

    /**
     * The overarching business status of the validation.
     */
    public ResponseStatus getStatus() {
        return status;
    }

    /**
     * An unmodifiable list of all normalized structural, semantic, and technical errors/warnings.
     */
    public List<FssError> getErrors() {
        return errors;
    }

    @Override
    public String toString() {
        return "ValidationResponse{" + "messageId='"
                + messageId + '\'' + ", status="
                + status + ", errors="
                + errors.size() + '}';
    }
}
