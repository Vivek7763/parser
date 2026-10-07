package com.prowidesoftware.swift.model.mx.validation;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import java.util.Collections;
import java.util.List;

/**
 * Structured result returned by {@link ISOParser#parse(String)}.
 *
 * <p>Holds four independent concerns in one object:
 * <ol>
 *   <li><b>Identity</b> — the parsed {@link ISOMessageIdentifier} (always populated when the message type can be
 *   determined from the XML namespace).</li>
 *   <li><b>Schema validation</b> — whether an XSD was found and whether the XML passed structural validation.</li>
 *   <li><b>Model parsing</b> — whether Prowide could instantiate a generated Java model object.</li>
 *   <li><b>Technical errors</b> — unexpected exceptions encountered during orchestration, distinct from
 *   business-level validation errors.</li>
 * </ol>
 *
 * <p>Callers should inspect {@link #getSchemaStatus()} first, then {@link #getModelStatus()}, then access
 * sub-objects as needed.
 */
public final class ISOParserResult {

    // -------------------------------------------------------------------------
    // Enumerations
    // -------------------------------------------------------------------------

    /**
     * Outcome of the XSD structural-validation phase.
     *
     * <ul>
     *   <li>{@link #PASS} – an XSD was found and the XML is structurally valid.</li>
     *   <li>{@link #FAIL} – an XSD was found but the XML failed one or more constraints; see
     *   {@link ISOParserResult#getValidationResult()} for details.</li>
     *   <li>{@link #SCHEMA_NOT_FOUND} – no XSD has been registered for this message type; structural validation
     *   was skipped (JAXB parsing may still succeed).</li>
     *   <li>{@link #UNIDENTIFIABLE_INPUT} – the XML could not be parsed well enough to extract a message type;
     *   no further processing was attempted.</li>
     * </ul>
     */
    public enum SchemaValidationStatus {
        PASS,
        FAIL,
        SCHEMA_NOT_FOUND,
        UNIDENTIFIABLE_INPUT
    }

    /**
     * Outcome of the Prowide JAXB model-parsing phase.
     *
     * <ul>
     *   <li>{@link #SUCCESS} – Prowide produced a non-null {@link AbstractMX} instance.</li>
     *   <li>{@link #PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR} – Prowide returned null. The root cause is
     *   indistinguishable via the public API: either the generated class does not exist in this deployment, or an
     *   internal parsing exception was swallowed by Prowide.</li>
     *   <li>{@link #SKIPPED} – model parsing was intentionally not attempted because schema validation failed
     *   ({@link SchemaValidationStatus#FAIL}).</li>
     * </ul>
     */
    public enum ModelParsingStatus {
        SUCCESS,
        PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR,
        SKIPPED
    }

    private final ISOMessageIdentifier identifier;
    private final SchemaValidationStatus schemaStatus;
    private final ModelParsingStatus modelStatus;
    private final ValidationResult validationResult;
    private final AbstractMX parsedModel;
    private final String technicalError;

    // -------------------------------------------------------------------------
    // Private constructor — use the builder
    // -------------------------------------------------------------------------

    private ISOParserResult(Builder b) {
        this.identifier = b.identifier;
        this.schemaStatus = b.schemaStatus;
        this.modelStatus = b.modelStatus;
        this.validationResult = b.validationResult;
        this.parsedModel = b.parsedModel;
        this.technicalError = b.technicalError;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the message identifier extracted from the XML namespace, or {@code null} when the input could not be
     * identified (in which case {@link #getSchemaStatus()} is {@link SchemaValidationStatus#UNIDENTIFIABLE_INPUT}).
     */
    public ISOMessageIdentifier getIdentifier() {
        return identifier;
    }

    /** Schema validation outcome. Never {@code null}. */
    public SchemaValidationStatus getSchemaStatus() {
        return schemaStatus;
    }

    /** Model parsing outcome. Never {@code null}. */
    public ModelParsingStatus getModelStatus() {
        return modelStatus;
    }

    /**
     * Structured XSD validation result including per-error line/column detail, or {@code null} when
     * {@link #getSchemaStatus()} is not {@link SchemaValidationStatus#PASS} or {@link SchemaValidationStatus#FAIL}.
     */
    public ValidationResult getValidationResult() {
        return validationResult;
    }

    /**
     * Returns validation errors from the XSD phase, or an empty list when validation was not performed or passed.
     * Convenience wrapper around {@link ValidationResult#getErrors()}.
     */
    public List<ValidationError> getValidationErrors() {
        return validationResult != null ? validationResult.getErrors() : Collections.emptyList();
    }

    /**
     * Returns the Prowide model object, or {@code null} when model parsing was skipped, failed, or the class was
     * unavailable.
     */
    public AbstractMX getParsedModel() {
        return parsedModel;
    }

    /**
     * Returns a human-readable description of an unexpected technical exception that occurred during orchestration,
     * or {@code null} when no technical error occurred.
     */
    public String getTechnicalError() {
        return technicalError;
    }

    /** Returns {@code true} when the XML passed XSD structural validation. */
    public boolean isSchemaValid() {
        return schemaStatus == SchemaValidationStatus.PASS;
    }

    /** Returns {@code true} when a Prowide model was successfully parsed. */
    public boolean isModelParsed() {
        return modelStatus == ModelParsingStatus.SUCCESS && parsedModel != null;
    }

    // -------------------------------------------------------------------------
    // Object overrides
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return "ISOParserResult{"
                + "identifier="
                + identifier
                + ", schemaStatus="
                + schemaStatus
                + ", modelStatus="
                + modelStatus
                + ", validationErrors="
                + getValidationErrors().size()
                + ", parsedModel="
                + (parsedModel != null ? parsedModel.getClass().getSimpleName() : "null")
                + (technicalError != null ? ", technicalError=" + technicalError : "")
                + '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /** Fluent builder for {@link ISOParserResult}. */
    public static final class Builder {
        private ISOMessageIdentifier identifier;
        private SchemaValidationStatus schemaStatus;
        private ModelParsingStatus modelStatus;
        private ValidationResult validationResult;
        private AbstractMX parsedModel;
        private String technicalError;

        public Builder identifier(ISOMessageIdentifier identifier) {
            this.identifier = identifier;
            return this;
        }

        public Builder schemaStatus(SchemaValidationStatus schemaStatus) {
            this.schemaStatus = schemaStatus;
            return this;
        }

        public Builder modelStatus(ModelParsingStatus modelStatus) {
            this.modelStatus = modelStatus;
            return this;
        }

        public Builder validationResult(ValidationResult validationResult) {
            this.validationResult = validationResult;
            return this;
        }

        public Builder parsedModel(AbstractMX parsedModel) {
            this.parsedModel = parsedModel;
            return this;
        }

        public Builder technicalError(String technicalError) {
            this.technicalError = technicalError;
            return this;
        }

        public ISOParserResult build() {
            if (schemaStatus == null) {
                throw new IllegalStateException("schemaStatus must be set");
            }
            if (modelStatus == null) {
                throw new IllegalStateException("modelStatus must be set");
            }
            if (schemaStatus == SchemaValidationStatus.FAIL) {
                if (modelStatus != ModelParsingStatus.SKIPPED) {
                    throw new IllegalStateException("Impossible state: schemaStatus is FAIL but modelStatus is "
                            + modelStatus
                            + " (must be SKIPPED)");
                }
                if (parsedModel != null) {
                    throw new IllegalStateException(
                            "Impossible state: schemaStatus is FAIL but parsedModel is non-null");
                }
            }
            if (schemaStatus == SchemaValidationStatus.PASS
                    || schemaStatus == SchemaValidationStatus.SCHEMA_NOT_FOUND) {
                if (modelStatus == ModelParsingStatus.SKIPPED) {
                    throw new IllegalStateException(
                            "Impossible state: schemaStatus is " + schemaStatus + " but modelStatus is SKIPPED");
                }
            }
            if (schemaStatus == SchemaValidationStatus.UNIDENTIFIABLE_INPUT) {
                if (modelStatus == ModelParsingStatus.SUCCESS || parsedModel != null) {
                    throw new IllegalStateException(
                            "Impossible state: schemaStatus is UNIDENTIFIABLE_INPUT but modelStatus is SUCCESS or parsedModel is non-null");
                }
                if (identifier != null) {
                    throw new IllegalStateException(
                            "Impossible state: schemaStatus is UNIDENTIFIABLE_INPUT but identifier is non-null");
                }
            }
            if (modelStatus == ModelParsingStatus.SUCCESS && parsedModel == null) {
                throw new IllegalStateException("Impossible state: modelStatus is SUCCESS but parsedModel is null");
            }
            if (modelStatus != ModelParsingStatus.SUCCESS && parsedModel != null) {
                throw new IllegalStateException(
                        "Impossible state: modelStatus is " + modelStatus + " but parsedModel is non-null");
            }
            return new ISOParserResult(this);
        }
    }
}
