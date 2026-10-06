package com.prowidesoftware.swift.model.mx.validation.builder;

import static org.assertj.core.api.Assertions.assertThat;

import com.fss.iso20022.api.ErrorCategory;
import com.fss.iso20022.api.FssError;
import com.fss.iso20022.api.ResponseStatus;
import com.fss.iso20022.api.ValidationResponse;
import com.prowidesoftware.swift.model.mx.AbstractMX;
import com.prowidesoftware.swift.model.mx.MxPacs00200112;
import com.prowidesoftware.swift.model.mx.validation.ISOMessageIdentifier;
import com.prowidesoftware.swift.model.mx.validation.ISOParserResult;
import com.prowidesoftware.swift.model.mx.validation.ValidationError;
import com.prowidesoftware.swift.model.mx.validation.ValidationResult;
import com.prowidesoftware.swift.model.mx.validation.semantic.SemanticValidationResult;
import com.prowidesoftware.swift.model.mx.validation.semantic.SemanticViolation;
import com.prowidesoftware.swift.model.mx.validation.semantic.TechnicalEvaluationError;
import org.junit.jupiter.api.Test;

class ValidationResponseBuilderTest {

    private final ISOMessageIdentifier mockId =
            ISOMessageIdentifier.fromNamespace("urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12");
    private final AbstractMX mockModel = new MxPacs00200112();

    @Test
    void testValidXml_AllPass() {
        ISOParserResult result = new ISOParserResult.Builder()
                .identifier(mockId)
                .schemaStatus(ISOParserResult.SchemaValidationStatus.PASS)
                .modelStatus(ISOParserResult.ModelParsingStatus.SUCCESS)
                .semanticStatus(ISOParserResult.SemanticValidationStatus.PASS)
                .parsedModel(mockModel)
                .build();

        ValidationResponse response = ValidationResponseBuilder.build(result);

        assertThat(response.getStatus()).isEqualTo(ResponseStatus.VALID);
        assertThat(response.getMessageId()).isEqualTo("pacs.002.001.12");
        assertThat(response.getErrors()).isEmpty();
    }

    @Test
    void testUnidentifiableInput() {
        ISOParserResult result = new ISOParserResult.Builder()
                .schemaStatus(ISOParserResult.SchemaValidationStatus.UNIDENTIFIABLE_INPUT)
                .modelStatus(ISOParserResult.ModelParsingStatus.SKIPPED)
                .semanticStatus(ISOParserResult.SemanticValidationStatus.SKIPPED)
                .build();

        ValidationResponse response = ValidationResponseBuilder.build(result);

        assertThat(response.getStatus()).isEqualTo(ResponseStatus.REJECTED);
        assertThat(response.getMessageId()).isNull();
        assertThat(response.getErrors()).hasSize(1);

        FssError error = response.getErrors().get(0);
        assertThat(error.getCategory()).isEqualTo(ErrorCategory.TECHNICAL);
        assertThat(error.getCode()).isEqualTo("UNIDENTIFIABLE_INPUT");
        assertThat(error.getSeverity()).isEqualTo("FATAL");
    }

    @Test
    void testSchemaNotFound_ModelSuccess() {
        ISOParserResult result = new ISOParserResult.Builder()
                .identifier(mockId)
                .schemaStatus(ISOParserResult.SchemaValidationStatus.SCHEMA_NOT_FOUND)
                .modelStatus(ISOParserResult.ModelParsingStatus.SUCCESS)
                .semanticStatus(ISOParserResult.SemanticValidationStatus.PASS)
                .parsedModel(mockModel)
                .build();

        ValidationResponse response = ValidationResponseBuilder.build(result);

        // Valid but contains a warning
        assertThat(response.getStatus()).isEqualTo(ResponseStatus.VALID);
        assertThat(response.getErrors()).hasSize(1);

        FssError error = response.getErrors().get(0);
        assertThat(error.getCategory()).isEqualTo(ErrorCategory.TECHNICAL);
        assertThat(error.getCode()).isEqualTo("SCHEMA_NOT_FOUND");
        assertThat(error.getSeverity()).isEqualTo("WARNING");
    }

    @Test
    void testXsdFailure() {
        ValidationResult xsdResult = new ValidationResult("pacs.002.001.12");
        xsdResult.addError(new ValidationError(ValidationError.ERROR, "Invalid length", 10, 20));

        ISOParserResult result = new ISOParserResult.Builder()
                .identifier(mockId)
                .schemaStatus(ISOParserResult.SchemaValidationStatus.FAIL)
                .validationResult(xsdResult)
                .modelStatus(ISOParserResult.ModelParsingStatus.SKIPPED)
                .semanticStatus(ISOParserResult.SemanticValidationStatus.SKIPPED)
                .build();

        ValidationResponse response = ValidationResponseBuilder.build(result);

        assertThat(response.getStatus()).isEqualTo(ResponseStatus.REJECTED);
        assertThat(response.getErrors()).hasSize(1);

        FssError error = response.getErrors().get(0);
        assertThat(error.getCategory()).isEqualTo(ErrorCategory.STRUCTURAL);
        assertThat(error.getCode()).isEqualTo("XSD_VIOLATION");
        assertThat(error.getMessage()).isEqualTo("Invalid length");
        assertThat(error.getLocation()).isEqualTo("Line: 10, Column: 20");
        assertThat(error.getSeverity()).isEqualTo("ERROR");
    }

    @Test
    void testModelUnavailable() {
        ISOParserResult result = new ISOParserResult.Builder()
                .identifier(mockId)
                .schemaStatus(ISOParserResult.SchemaValidationStatus.PASS)
                .modelStatus(ISOParserResult.ModelParsingStatus.PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR)
                .semanticStatus(ISOParserResult.SemanticValidationStatus.SKIPPED)
                .build();

        ValidationResponse response = ValidationResponseBuilder.build(result);

        assertThat(response.getStatus()).isEqualTo(ResponseStatus.SYSTEM_ERROR);
        assertThat(response.getErrors()).hasSize(1);

        FssError error = response.getErrors().get(0);
        assertThat(error.getCategory()).isEqualTo(ErrorCategory.TECHNICAL);
        assertThat(error.getCode()).isEqualTo("MODEL_UNAVAILABLE");
        assertThat(error.getSeverity()).isEqualTo("FATAL");
    }

    @Test
    void testSemanticFailure() {
        SemanticValidationResult semResult = new SemanticValidationResult();
        semResult.addViolation(new SemanticViolation("RULE_001", "ERROR", "MsgId missing", "GrpHdr/MsgId"));

        ISOParserResult result = new ISOParserResult.Builder()
                .identifier(mockId)
                .schemaStatus(ISOParserResult.SchemaValidationStatus.PASS)
                .modelStatus(ISOParserResult.ModelParsingStatus.SUCCESS)
                .parsedModel(mockModel)
                .semanticStatus(ISOParserResult.SemanticValidationStatus.FAIL)
                .semanticResult(semResult)
                .build();

        ValidationResponse response = ValidationResponseBuilder.build(result);

        assertThat(response.getStatus()).isEqualTo(ResponseStatus.REJECTED);
        assertThat(response.getErrors()).hasSize(1);

        FssError error = response.getErrors().get(0);
        assertThat(error.getCategory()).isEqualTo(ErrorCategory.SEMANTIC);
        assertThat(error.getCode()).isEqualTo("RULE_001");
        assertThat(error.getMessage()).isEqualTo("MsgId missing");
        assertThat(error.getLocation()).isEqualTo("GrpHdr/MsgId");
        assertThat(error.getSeverity()).isEqualTo("ERROR");
    }

    @Test
    void testSemanticTechnicalError() {
        SemanticValidationResult semResult = new SemanticValidationResult();
        semResult.addTechnicalError(new TechnicalEvaluationError("RULE_002", "SpEL parse crash"));

        ISOParserResult result = new ISOParserResult.Builder()
                .identifier(mockId)
                .schemaStatus(ISOParserResult.SchemaValidationStatus.PASS)
                .modelStatus(ISOParserResult.ModelParsingStatus.SUCCESS)
                .parsedModel(mockModel)
                .semanticStatus(ISOParserResult.SemanticValidationStatus.TECHNICAL_ERROR)
                .semanticResult(semResult)
                .build();

        ValidationResponse response = ValidationResponseBuilder.build(result);

        assertThat(response.getStatus()).isEqualTo(ResponseStatus.SYSTEM_ERROR);
        assertThat(response.getErrors()).hasSize(1);

        FssError error = response.getErrors().get(0);
        assertThat(error.getCategory()).isEqualTo(ErrorCategory.TECHNICAL);
        assertThat(error.getCode()).isEqualTo("SEMANTIC_TECH_ERROR");
        assertThat(error.getMessage()).isEqualTo("SpEL parse crash");
        assertThat(error.getLocation()).isEqualTo("RULE_002");
        assertThat(error.getSeverity()).isEqualTo("FATAL");
    }

    @Test
    void testGlobalTechnicalError() {
        ISOParserResult result = new ISOParserResult.Builder()
                .identifier(mockId)
                .schemaStatus(ISOParserResult.SchemaValidationStatus.FAIL)
                .modelStatus(ISOParserResult.ModelParsingStatus.SKIPPED)
                .semanticStatus(ISOParserResult.SemanticValidationStatus.SKIPPED)
                .technicalError("Schema compilation crashed")
                .build();

        ValidationResponse response = ValidationResponseBuilder.build(result);

        assertThat(response.getStatus()).isEqualTo(ResponseStatus.SYSTEM_ERROR);

        // Error 1: XSD FAIL leads to XSD_VIOLATION?
        // Wait, if schemaStatus is FAIL but validationResult is null (because it crashed), we won't get XSD violations.
        // But we will get the technical error.
        assertThat(response.getErrors()).hasSize(1);
        FssError error = response.getErrors().get(0);
        assertThat(error.getCategory()).isEqualTo(ErrorCategory.TECHNICAL);
        assertThat(error.getCode()).isEqualTo("SYSTEM_ERROR");
        assertThat(error.getMessage()).isEqualTo("Schema compilation crashed");
        assertThat(error.getSeverity()).isEqualTo("FATAL");
    }
}
