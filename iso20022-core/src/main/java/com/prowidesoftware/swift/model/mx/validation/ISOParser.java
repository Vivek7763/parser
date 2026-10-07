package com.prowidesoftware.swift.model.mx.validation;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * FSS-owned ISO 20022 parser orchestrator.
 *
 * <h2>Processing pipeline</h2>
 * <pre>
 * XML input
 *   │
 *   ▼
 * [1] IDENTIFICATION  ── ProwideAdapter.extractIdentifier(xml)
 *       │ null → UNIDENTIFIABLE_INPUT (stop)
 *       │
 *   ▼
 * [2] SCHEMA RESOLUTION ── SchemaRegistry.resolve(identifier)
 *       │ SchemaNotFoundException  → SCHEMA_NOT_FOUND  (continue to step 4)
 *       │ SchemaCompilationException → FAIL + technicalError (stop)
 *       │
 *   ▼
 * [3] STRUCTURAL VALIDATION ── ISOValidator.validate(xml, identifier)
 *       │ errors present → FAIL (stop: Prowide NOT invoked)
 *       │ no errors      → PASS (continue)
 *       │
 *   ▼
 * [4] MODEL PARSING ── ProwideAdapter.parseToModel(xml)
 *       │ null    → PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR
 *       │ non-null → SUCCESS
 *       │
 *   ▼
 * ISOParserResult
 * </pre>
 *
 * <h2>Key design decisions</h2>
 * <ul>
 *   <li>Prowide is never invoked before XSD validation passes (or is absent).</li>
 *   <li>Schema-not-found is deliberately non-blocking: validation is skipped, but JAXB parsing still runs.
 *   This allows the parser to function during the schema catalogue build-out phase.</li>
 *   <li>All Prowide API calls are confined to {@link ProwideAdapter}.</li>
 *   <li>Error classification uses typed exceptions from {@link SchemaRegistry} — no brittle string matching.</li>
 * </ul>
 */
public final class ISOParser {

    private static final Logger log = Logger.getLogger(ISOParser.class.getName());

    private ISOParser() {}

    /**
     * Parses and validates an ISO 20022 XML message without semantic validation.
     *
     * @param xml the raw XML string; must not be {@code null}
     * @return a fully-populated {@link ISOParserResult}; never {@code null}
     * @throws IllegalArgumentException if {@code xml} is {@code null}
     */
    public static ISOParserResult parse(String xml) {
        if (xml == null) {
            throw new IllegalArgumentException("xml must not be null");
        }

        // ── STEP 1: IDENTIFICATION ─────────────────────────────────────────
        MessageStructureInfo info = MessageStructureInfo.parse(xml);
        ISOMessageIdentifier identifier = ProwideAdapter.extractIdentifier(xml);
        if (identifier == null || !identifier.isWellFormed()) {
            return new ISOParserResult.Builder()
                    .identifier(null)
                    .schemaStatus(ISOParserResult.SchemaValidationStatus.UNIDENTIFIABLE_INPUT)
                    .modelStatus(ISOParserResult.ModelParsingStatus.PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR)
                    .build();
        }

        // AppHdr/Document mismatch check
        String msgDefIdr = info.getMsgDefIdr();
        ValidationResult validationResult = null;
        if (info.getAppHdrIdentifier() != null && msgDefIdr != null) {
            if (!msgDefIdr.equals(identifier.getFullMessageType())) {
                validationResult = new ValidationResult(identifier.getFullMessageType());
                validationResult.addError(new ValidationError(
                        "FATAL",
                        "AppHdr MsgDefIdr '" + msgDefIdr + "' does not match Document namespace '"
                                + identifier.getFullMessageType() + "'",
                        -1,
                        -1));
                return new ISOParserResult.Builder()
                        .identifier(identifier)
                        .schemaStatus(ISOParserResult.SchemaValidationStatus.FAIL)
                        .modelStatus(ISOParserResult.ModelParsingStatus.SKIPPED)
                        .validationResult(validationResult)
                        .technicalError("AppHdr/Document mismatch")
                        .build();
            }
        }

        // ── STEP 2 & 3: SCHEMA RESOLUTION + STRUCTURAL VALIDATION ─────────
        ISOParserResult.SchemaValidationStatus schemaStatus;
        String technicalError = null;

        try {
            validationResult = ISOValidator.validate(xml, identifier, info.getAppHdrIdentifier());
            schemaStatus = validationResult.isValid()
                    ? ISOParserResult.SchemaValidationStatus.PASS
                    : ISOParserResult.SchemaValidationStatus.FAIL;
        } catch (SchemaRegistry.SchemaNotFoundException e) {
            // No XSD on disk for this message type — non-blocking
            schemaStatus = ISOParserResult.SchemaValidationStatus.SCHEMA_NOT_FOUND;
        } catch (SchemaRegistry.SchemaCompilationException e) {
            // XSD exists but is corrupt/invalid — treat as a hard FAIL
            schemaStatus = ISOParserResult.SchemaValidationStatus.FAIL;
            technicalError = "XSD compilation error: " + e.getMessage();
            if (validationResult == null) {
                validationResult = new ValidationResult(identifier != null ? identifier.getFullMessageType() : null);
            }
            if (validationResult.isValid()) {
                validationResult.addError(new ValidationError("FATAL", technicalError, -1, -1));
            }
            log.log(Level.WARNING, "XSD compilation failed for " + identifier, e);
        } catch (Exception e) {
            // Unexpected SAX infrastructure failure during validation
            schemaStatus = ISOParserResult.SchemaValidationStatus.FAIL;
            technicalError = "Unexpected validation error: " + e.getMessage();
            if (validationResult == null) {
                validationResult = new ValidationResult(identifier != null ? identifier.getFullMessageType() : null);
            }
            if (validationResult.isValid()) {
                if (e instanceof org.xml.sax.SAXParseException) {
                    org.xml.sax.SAXParseException spe = (org.xml.sax.SAXParseException) e;
                    validationResult.addError(
                            new ValidationError("FATAL", spe.getMessage(), spe.getLineNumber(), spe.getColumnNumber()));
                } else if (e.getCause() instanceof org.xml.sax.SAXParseException) {
                    org.xml.sax.SAXParseException spe = (org.xml.sax.SAXParseException) e.getCause();
                    validationResult.addError(
                            new ValidationError("FATAL", spe.getMessage(), spe.getLineNumber(), spe.getColumnNumber()));
                } else {
                    validationResult.addError(new ValidationError("FATAL", e.getMessage(), -1, -1));
                }
            }
            log.log(Level.WARNING, "Unexpected error validating " + identifier, e);
        }

        // Schema FAIL → do NOT invoke Prowide or Semantic
        if (schemaStatus == ISOParserResult.SchemaValidationStatus.FAIL) {
            return new ISOParserResult.Builder()
                    .identifier(identifier)
                    .schemaStatus(schemaStatus)
                    .modelStatus(ISOParserResult.ModelParsingStatus.SKIPPED)
                    .validationResult(validationResult)
                    .technicalError(technicalError)
                    .build();
        }

        // ── STEP 4: MODEL PARSING ──────────────────────────────────────────
        AbstractMX parsedModel = ProwideAdapter.parseToModel(xml);
        ISOParserResult.ModelParsingStatus modelStatus = parsedModel != null
                ? ISOParserResult.ModelParsingStatus.SUCCESS
                : ISOParserResult.ModelParsingStatus.PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR;

        return new ISOParserResult.Builder()
                .identifier(identifier)
                .schemaStatus(schemaStatus)
                .modelStatus(modelStatus)
                .validationResult(validationResult)
                .parsedModel(parsedModel)
                .technicalError(technicalError)
                .build();
    }

    /**
     * @deprecated Use {@link #parse(String)} instead.
     */
    @Deprecated
    public static ISOParserResult parseAndValidate(String xml) {
        return parse(xml);
    }
}
