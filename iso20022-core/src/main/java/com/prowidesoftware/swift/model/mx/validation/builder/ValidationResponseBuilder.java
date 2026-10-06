package com.prowidesoftware.swift.model.mx.validation.builder;

import com.fss.iso20022.api.ErrorCategory;
import com.fss.iso20022.api.FssError;
import com.fss.iso20022.api.ResponseStatus;
import com.fss.iso20022.api.ValidationResponse;
import com.prowidesoftware.swift.model.mx.validation.ISOParserResult;
import com.prowidesoftware.swift.model.mx.validation.ValidationError;
import com.prowidesoftware.swift.model.mx.validation.semantic.SemanticValidationResult;
import com.prowidesoftware.swift.model.mx.validation.semantic.SemanticViolation;
import com.prowidesoftware.swift.model.mx.validation.semantic.TechnicalEvaluationError;
import java.util.ArrayList;
import java.util.List;

/**
 * Mapper responsible for converting the internal orchestration result (ISOParserResult)
 * into the public, Prowide-independent contract (ValidationResponse).
 *
 * This builder performs NO validation itself; it only maps existing statuses and errors.
 */
public final class ValidationResponseBuilder {

    private ValidationResponseBuilder() {
        // Prevent instantiation
    }

    public static ValidationResponse build(ISOParserResult result) {
        String messageId =
                result.getIdentifier() != null ? result.getIdentifier().getFullMessageType() : null;
        List<FssError> errors = new ArrayList<>();
        ResponseStatus status = determineStatusAndMapErrors(result, errors);

        return new ValidationResponse(messageId, status, errors);
    }

    private static ResponseStatus determineStatusAndMapErrors(ISOParserResult result, List<FssError> errors) {
        // 1. Unidentifiable Input (Hard Technical Failure)
        if (result.getSchemaStatus() == ISOParserResult.SchemaValidationStatus.UNIDENTIFIABLE_INPUT) {
            errors.add(new FssError(
                    ErrorCategory.TECHNICAL,
                    "UNIDENTIFIABLE_INPUT",
                    "Could not identify ISO 20022 message type from namespace",
                    null,
                    "FATAL"));
            return ResponseStatus.REJECTED;
        }

        // 2. Global Technical Errors (e.g. schema compilation crash)
        // Checked early because an infrastructure crash overrides standard validation statuses.
        if (result.getTechnicalError() != null) {
            errors.add(
                    new FssError(ErrorCategory.TECHNICAL, "SYSTEM_ERROR", result.getTechnicalError(), null, "FATAL"));
            return ResponseStatus.SYSTEM_ERROR;
        }

        // 3. Schema Not Found (Non-blocking warning)
        if (result.getSchemaStatus() == ISOParserResult.SchemaValidationStatus.SCHEMA_NOT_FOUND) {
            errors.add(new FssError(
                    ErrorCategory.TECHNICAL,
                    "SCHEMA_NOT_FOUND",
                    "No XSD registered for message type: "
                            + result.getIdentifier().getFullMessageType(),
                    null,
                    "WARNING"));
        }

        // 4. XSD Validation Failures
        if (result.getSchemaStatus() == ISOParserResult.SchemaValidationStatus.FAIL) {
            for (ValidationError ve : result.getValidationErrors()) {
                String loc = ve.getLine() > 0 ? "Line: " + ve.getLine() + ", Column: " + ve.getColumn() : null;
                errors.add(new FssError(
                        ErrorCategory.STRUCTURAL, "XSD_VIOLATION", ve.getMessage(), loc, ve.getSeverity()));
            }
            return ResponseStatus.REJECTED;
        }

        // 5. Model Parsing Failures
        if (result.getModelStatus() == ISOParserResult.ModelParsingStatus.PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR) {
            errors.add(new FssError(
                    ErrorCategory.TECHNICAL,
                    "MODEL_UNAVAILABLE",
                    "Prowide JAXB model unavailable or internal parse error",
                    null,
                    "FATAL"));
            return ResponseStatus.SYSTEM_ERROR;
        }

        // 6. Semantic Validation Results
        SemanticValidationResult semResult = result.getSemanticResult();
        if (semResult != null) {
            // Semantic Technical Errors
            for (TechnicalEvaluationError tee : semResult.getTechnicalErrors()) {
                errors.add(new FssError(
                        ErrorCategory.TECHNICAL,
                        "SEMANTIC_TECH_ERROR",
                        tee.getErrorMessage(),
                        tee.getRuleId(),
                        "FATAL"));
            }

            // Semantic Violations
            for (SemanticViolation sv : semResult.getViolations()) {
                errors.add(new FssError(
                        ErrorCategory.SEMANTIC, sv.getRuleId(), sv.getMessage(), sv.getErrorPath(), sv.getSeverity()));
            }

            if (result.getSemanticStatus() == ISOParserResult.SemanticValidationStatus.TECHNICAL_ERROR) {
                return ResponseStatus.SYSTEM_ERROR;
            }
            if (result.getSemanticStatus() == ISOParserResult.SemanticValidationStatus.FAIL) {
                return ResponseStatus.REJECTED;
            }
        }

        // If we reach here, no hard rejections or fatal system errors occurred.
        return ResponseStatus.VALID;
    }
}
