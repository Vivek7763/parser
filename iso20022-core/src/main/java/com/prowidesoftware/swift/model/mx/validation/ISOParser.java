package com.prowidesoftware.swift.model.mx.validation;

import com.prowidesoftware.swift.model.mx.AbstractMX;
import com.prowidesoftware.swift.model.mx.validation.semantic.SemanticRuleEngine;
import com.prowidesoftware.swift.model.mx.validation.semantic.SemanticValidationResult;
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
        return parse(xml, null);
    }

    /**
     * Parses and validates an ISO 20022 XML message with optional semantic validation.
     *
     * @param xml the raw XML string; must not be {@code null}
     * @param semanticEngine the semantic engine to execute business rules (can be {@code null} to skip semantic validation)
     * @return a fully-populated {@link ISOParserResult}; never {@code null}
     * @throws IllegalArgumentException if {@code xml} is {@code null}
     */
    public static ISOParserResult parse(String xml, SemanticRuleEngine semanticEngine) {
        if (xml == null) {
            throw new IllegalArgumentException("xml must not be null");
        }

        // ── STEP 1: IDENTIFICATION ─────────────────────────────────────────
        ISOMessageIdentifier identifier = ProwideAdapter.extractIdentifier(xml);
        if (identifier == null || !identifier.isWellFormed()) {
            return new ISOParserResult.Builder()
                    .identifier(null)
                    .schemaStatus(ISOParserResult.SchemaValidationStatus.UNIDENTIFIABLE_INPUT)
                    .modelStatus(ISOParserResult.ModelParsingStatus.PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR)
                    .semanticStatus(ISOParserResult.SemanticValidationStatus.SKIPPED)
                    .build();
        }

        // ── STEP 2 & 3: SCHEMA RESOLUTION + STRUCTURAL VALIDATION ─────────
        ISOParserResult.SchemaValidationStatus schemaStatus;
        ValidationResult validationResult = null;
        String technicalError = null;

        try {
            validationResult = ISOValidator.validate(xml, identifier);
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
                    .semanticStatus(ISOParserResult.SemanticValidationStatus.SKIPPED)
                    .validationResult(validationResult)
                    .technicalError(technicalError)
                    .build();
        }

        // ── STEP 4: MODEL PARSING ──────────────────────────────────────────
        AbstractMX parsedModel = ProwideAdapter.parseToModel(xml);
        ISOParserResult.ModelParsingStatus modelStatus = parsedModel != null
                ? ISOParserResult.ModelParsingStatus.SUCCESS
                : ISOParserResult.ModelParsingStatus.PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR;

        // ── STEP 5: SEMANTIC VALIDATION ────────────────────────────────────
        ISOParserResult.SemanticValidationStatus semanticStatus = ISOParserResult.SemanticValidationStatus.SKIPPED;
        SemanticValidationResult semanticResult = null;

        if (modelStatus == ISOParserResult.ModelParsingStatus.SUCCESS && semanticEngine != null) {
            try {
                semanticResult = semanticEngine.validate(parsedModel);
                if (!semanticResult.getTechnicalErrors().isEmpty()) {
                    semanticStatus = ISOParserResult.SemanticValidationStatus.TECHNICAL_ERROR;
                } else if (semanticResult.hasFailures()) {
                    semanticStatus = ISOParserResult.SemanticValidationStatus.FAIL;
                } else if (semanticResult.getEvaluatedRuleCount() == 0) {
                    semanticStatus = ISOParserResult.SemanticValidationStatus.NOT_APPLICABLE;
                } else {
                    semanticStatus = ISOParserResult.SemanticValidationStatus.PASS;
                }
            } catch (Exception e) {
                // Failsafe catch around the engine itself
                semanticStatus = ISOParserResult.SemanticValidationStatus.TECHNICAL_ERROR;
                technicalError = technicalError == null
                        ? "Semantic Engine Error: " + e.getMessage()
                        : technicalError + " | Semantic Engine Error: " + e.getMessage();
                log.log(Level.WARNING, "Semantic Engine encountered unexpected exception", e);
            }
        }

        return new ISOParserResult.Builder()
                .identifier(identifier)
                .schemaStatus(schemaStatus)
                .modelStatus(modelStatus)
                .semanticStatus(semanticStatus)
                .validationResult(validationResult)
                .parsedModel(parsedModel)
                .semanticResult(semanticResult)
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
