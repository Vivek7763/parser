package com.prowidesoftware.swift.model.mx;

import static org.junit.jupiter.api.Assertions.*;

import com.prowidesoftware.swift.model.mx.validation.*;
import com.prowidesoftware.swift.model.mx.validation.ISOParserResult.ModelParsingStatus;
import com.prowidesoftware.swift.model.mx.validation.ISOParserResult.SchemaValidationStatus;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Hardening and audit verification test suite for the FSS ISO 20022 parser orchestration layer.
 *
 * <p>Exercises all 10 architectural checkpoints defined in the hardening specification.
 */
public class ISOParserHardeningAuditTest {

    // ═══════════════════════════════════════════════════════════════════════
    // 1. MESSAGE IDENTIFICATION
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void test01_identification_standardMessageTypes() {
        // pacs.002.001.12
        ISOMessageIdentifier pacs002 =
                ISOMessageIdentifier.fromNamespace("urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12");
        assertNotNull(pacs002);
        assertTrue(pacs002.isWellFormed());
        assertEquals("pacs", pacs002.getBusinessArea());
        assertEquals("002", pacs002.getMessageFunction());
        assertEquals("001", pacs002.getVariant());
        assertEquals("12", pacs002.getVersion());
        assertEquals("pacs.002.001.12", pacs002.getFullMessageType());

        // pacs.008.001.14
        ISOMessageIdentifier pacs008 =
                ISOMessageIdentifier.fromNamespace("urn:iso:std:iso:20022:tech:xsd:pacs.008.001.14");
        assertNotNull(pacs008);
        assertTrue(pacs008.isWellFormed());
        assertEquals("pacs", pacs008.getBusinessArea());
        assertEquals("008", pacs008.getMessageFunction());
        assertEquals("001", pacs008.getVariant());
        assertEquals("14", pacs008.getVersion());
        assertEquals("pacs.008.001.14", pacs008.getFullMessageType());

        // camt.053.001.08
        ISOMessageIdentifier camt053 =
                ISOMessageIdentifier.fromNamespace("urn:iso:std:iso:20022:tech:xsd:camt.053.001.08");
        assertNotNull(camt053);
        assertTrue(camt053.isWellFormed());
        assertEquals("camt", camt053.getBusinessArea());
        assertEquals("053", camt053.getMessageFunction());
        assertEquals("001", camt053.getVariant());
        assertEquals("08", camt053.getVersion());
        assertEquals("camt.053.001.08", camt053.getFullMessageType());

        // seev.031.001.09
        ISOMessageIdentifier seev031 =
                ISOMessageIdentifier.fromNamespace("urn:iso:std:iso:20022:tech:xsd:seev.031.001.09");
        assertNotNull(seev031);
        assertTrue(seev031.isWellFormed());
        assertEquals("seev", seev031.getBusinessArea());
        assertEquals("031", seev031.getMessageFunction());
        assertEquals("001", seev031.getVariant());
        assertEquals("09", seev031.getVersion());
        assertEquals("seev.031.001.09", seev031.getFullMessageType());
    }

    @Test
    void test01_identification_unknownBusinessArea_notReportedAsSchemaError() {
        // Namespace with unknown business area "zzzz"
        ISOMessageIdentifier id = ISOMessageIdentifier.fromNamespace("urn:iso:std:iso:20022:tech:xsd:zzzz.999.999.99");
        assertNotNull(id);
        assertEquals("zzzz", id.getBusinessArea());
        assertTrue(id.isWellFormed());

        // When parsed through ISOParser: Prowide's MxId rejects unknown business process enum,
        // resulting in UNIDENTIFIABLE_INPUT, NOT a schema error (not FAIL, not SCHEMA_NOT_FOUND)
        String xml = "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:zzzz.999.999.99\"><Test/></Document>";
        ISOParserResult result = ISOParser.parse(xml);
        assertNotNull(result);
        assertEquals(SchemaValidationStatus.UNIDENTIFIABLE_INPUT, result.getSchemaStatus());
        assertNotEquals(SchemaValidationStatus.FAIL, result.getSchemaStatus());
        assertNotEquals(SchemaValidationStatus.SCHEMA_NOT_FOUND, result.getSchemaStatus());
        assertNull(result.getIdentifier());
        assertNull(result.getParsedModel());
    }

    @Test
    void test01_identification_malformedNamespace_notReportedAsSchemaError() {
        // Malformed namespace tokens
        ISOMessageIdentifier invalid1 = ISOMessageIdentifier.fromNamespace("urn:iso:std:iso:20022:tech:xsd:invalid");
        assertNotNull(invalid1);
        assertFalse(invalid1.isWellFormed());
        assertNull(invalid1.getBusinessArea());

        ISOMessageIdentifier invalid2 = ISOMessageIdentifier.fromNamespace("not-a-namespace");
        assertNotNull(invalid2);
        assertFalse(invalid2.isWellFormed());

        // Parse XML with malformed namespace: must result in UNIDENTIFIABLE_INPUT
        String xml = "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:notvalid\"><Body/></Document>";
        ISOParserResult result = ISOParser.parse(xml);
        assertEquals(SchemaValidationStatus.UNIDENTIFIABLE_INPUT, result.getSchemaStatus());
        assertNull(result.getIdentifier());
        assertNull(result.getParsedModel());
    }

    @Test
    void test01_identification_missingNamespace_notReportedAsSchemaError() {
        assertNull(ISOMessageIdentifier.fromNamespace(null));

        String xml = "<Document><FIToFIPmtStsRpt><MsgId>M1</MsgId></FIToFIPmtStsRpt></Document>";
        ISOParserResult result = ISOParser.parse(xml);
        assertEquals(SchemaValidationStatus.UNIDENTIFIABLE_INPUT, result.getSchemaStatus());
        assertNull(result.getIdentifier());
        assertNull(result.getParsedModel());
    }

    @Test
    void test01_identification_namespacePresentButTruncatedXml_identifiedThenFailsSchema() {
        // Namespace is present so message is identified, but XML syntax is truncated
        String xml = "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\"><FIToFIPmtStsRpt>";
        ISOParserResult result = ISOParser.parse(xml);

        // Correctly identified
        assertNotNull(result.getIdentifier());
        assertEquals("pacs.002.001.12", result.getIdentifier().getFullMessageType());

        // Validation fails because of XML malformedness/truncation
        assertEquals(SchemaValidationStatus.FAIL, result.getSchemaStatus());
        assertEquals(ModelParsingStatus.SKIPPED, result.getModelStatus());
        assertNull(result.getParsedModel());
    }

    @Test
    void test01_identification_directNamespaceOnDocument() {
        // XML where namespace is declared directly on Document: <Document xmlns="urn:...">
        String xml = "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\">"
                + "<FIToFIPmtStsRpt><GrpHdr><MsgId>MSGID123</MsgId>"
                + "<CreDtTm>2023-10-01T12:00:00Z</CreDtTm></GrpHdr>"
                + "<TxInfAndSts><OrgnlInstrId>INSTR123</OrgnlInstrId><TxSts>ACCP</TxSts></TxInfAndSts>"
                + "</FIToFIPmtStsRpt></Document>";

        ISOParserResult result = ISOParser.parse(xml);
        assertNotNull(result.getIdentifier());
        assertEquals("pacs.002.001.12", result.getIdentifier().getFullMessageType());
        assertEquals("pacs", result.getIdentifier().getBusinessArea());
        assertEquals(SchemaValidationStatus.PASS, result.getSchemaStatus());
        assertEquals(ModelParsingStatus.SUCCESS, result.getModelStatus());
        assertNotNull(result.getParsedModel());
        assertTrue(result.getValidationErrors().isEmpty());
    }

    @Test
    void test01_identification_inheritedDefaultNamespace() {
        // XML where namespace is declared on an ancestor wrapper element: <Message xmlns="urn:..."><Document>...
        String xml = "<Message xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\">"
                + "<Document><FIToFIPmtStsRpt><GrpHdr><MsgId>MSGID123</MsgId>"
                + "<CreDtTm>2023-10-01T12:00:00Z</CreDtTm></GrpHdr>"
                + "<TxInfAndSts><OrgnlInstrId>INSTR123</OrgnlInstrId><TxSts>ACCP</TxSts></TxInfAndSts>"
                + "</FIToFIPmtStsRpt></Document></Message>";

        ISOParserResult result = ISOParser.parse(xml);
        assertNotNull(result.getIdentifier());
        assertEquals("pacs.002.001.12", result.getIdentifier().getFullMessageType());
        assertEquals("pacs", result.getIdentifier().getBusinessArea());
        assertEquals(SchemaValidationStatus.PASS, result.getSchemaStatus());
        assertEquals(ModelParsingStatus.SUCCESS, result.getModelStatus());
        assertNotNull(result.getParsedModel());
        assertTrue(result.getValidationErrors().isEmpty());
    }

    @Test
    void test01_identification_inheritedPrefixedNamespace_withPrefixedDocument() {
        // XML where namespace is declared with prefix on ancestor and used by Document: <doc:Document>
        String xml = "<doc:Message xmlns:doc=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\">"
                + "<doc:Document><doc:FIToFIPmtStsRpt><doc:GrpHdr><doc:MsgId>MSGID123</doc:MsgId>"
                + "<doc:CreDtTm>2023-10-01T12:00:00Z</doc:CreDtTm></doc:GrpHdr>"
                + "<doc:TxInfAndSts><doc:OrgnlInstrId>INSTR123</doc:OrgnlInstrId><doc:TxSts>ACCP</doc:TxSts></doc:TxInfAndSts>"
                + "</doc:FIToFIPmtStsRpt></doc:Document></doc:Message>";

        ISOParserResult result = ISOParser.parse(xml);
        assertNotNull(result.getIdentifier());
        assertEquals("pacs.002.001.12", result.getIdentifier().getFullMessageType());
        assertEquals("pacs", result.getIdentifier().getBusinessArea());
        assertEquals(SchemaValidationStatus.PASS, result.getSchemaStatus());
        assertEquals(ModelParsingStatus.SUCCESS, result.getModelStatus());
        assertNotNull(result.getParsedModel());
        assertTrue(result.getValidationErrors().isEmpty());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 2. SCHEMA STATES
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void test02_schemaStates_allFiveStatesDistinct() throws Exception {
        // State 1: schema PASS
        ISOParserResult passResult = ISOParser.parse(validPacs002Xml());
        assertEquals(SchemaValidationStatus.PASS, passResult.getSchemaStatus());
        assertTrue(passResult.isSchemaValid());
        assertTrue(passResult.getValidationErrors().isEmpty());
        assertNull(passResult.getTechnicalError());

        // State 2: schema FAIL
        ISOParserResult failResult = ISOParser.parse(invalidPacs002Xml());
        assertEquals(SchemaValidationStatus.FAIL, failResult.getSchemaStatus());
        assertFalse(failResult.isSchemaValid());
        assertFalse(failResult.getValidationErrors().isEmpty());
        assertEquals(ModelParsingStatus.SKIPPED, failResult.getModelStatus());
        assertNull(failResult.getTechnicalError());

        // State 3: schema NOT FOUND
        ISOParserResult notFoundResult = ISOParser.parse(validCamt053Xml());
        assertEquals(SchemaValidationStatus.SCHEMA_NOT_FOUND, notFoundResult.getSchemaStatus());
        assertFalse(notFoundResult.isSchemaValid());
        assertNull(notFoundResult.getValidationResult());

        // State 4: schema COMPILATION FAILURE (technical error, NOT treated as schema not found)
        Path tempDir = Files.createTempDirectory("corrupt_schema_test");
        try {
            Path pacsDir = tempDir.resolve("pacs").resolve("pacs.999.001.01");
            Files.createDirectories(pacsDir);
            Path corruptXsd = pacsDir.resolve("pacs.999.001.01.xsd");
            Files.writeString(corruptXsd, "CORRUPT NOT AN XSD CONTENT <><>");

            SchemaRegistry.setBaseSchemasDir(tempDir.toAbsolutePath().toString());

            // Direct resolution test
            ISOMessageIdentifier corruptId =
                    ISOMessageIdentifier.fromNamespace("urn:iso:std:iso:20022:tech:xsd:pacs.999.001.01");
            assertThrows(SchemaRegistry.SchemaCompilationException.class, () -> SchemaRegistry.resolve(corruptId));

            // Parser flow test: compilation error must be FAIL with technicalError, NOT SCHEMA_NOT_FOUND
            String corruptMsgXml =
                    "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.999.001.01\"><Test/></Document>";
            ISOParserResult compFailResult = ISOParser.parse(corruptMsgXml);
            assertEquals(SchemaValidationStatus.FAIL, compFailResult.getSchemaStatus());
            assertEquals(ModelParsingStatus.SKIPPED, compFailResult.getModelStatus());
            assertNotNull(compFailResult.getTechnicalError());
            assertTrue(
                    compFailResult.getTechnicalError().contains("XSD compilation error"),
                    "Technical error must describe compilation failure");
        } finally {
            // Restore default schema base directory
            SchemaRegistry.setBaseSchemasDir(SchemaRegistry.DEFAULT_SCHEMAS_BASE_DIR);
            deleteRecursively(tempDir.toFile());
        }

        // State 5: UNIDENTIFIABLE INPUT
        ISOParserResult unidentifiableResult = ISOParser.parse("<InvalidXml></InvalidXml>");
        assertEquals(SchemaValidationStatus.UNIDENTIFIABLE_INPUT, unidentifiableResult.getSchemaStatus());
        assertNull(unidentifiableResult.getIdentifier());
        assertNull(unidentifiableResult.getParsedModel());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 3. VALIDATION BOUNDARY
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void test03_validationBoundary_invalidXmlNeverReachesProwideParseToModel() {
        ProwideAdapter.resetParseToModelCallCount();

        // pacs.002 invalid XML (violating Max35Text on MsgId)
        ISOParserResult result = ISOParser.parse(invalidPacs002Xml());

        assertEquals(SchemaValidationStatus.FAIL, result.getSchemaStatus());
        assertEquals(ModelParsingStatus.SKIPPED, result.getModelStatus());
        assertNull(result.getParsedModel());

        // Strictly verify that ProwideAdapter.parseToModel was NEVER called!
        assertEquals(
                0,
                ProwideAdapter.getParseToModelCallCount(),
                "ProwideAdapter.parseToModel() must NOT be invoked when schema validation fails!");

        // Now verify that a valid XML DOES invoke parseToModel
        ISOParserResult validResult = ISOParser.parse(validPacs002Xml());
        assertEquals(SchemaValidationStatus.PASS, validResult.getSchemaStatus());
        assertEquals(ModelParsingStatus.SUCCESS, validResult.getModelStatus());
        assertEquals(
                1,
                ProwideAdapter.getParseToModelCallCount(),
                "ProwideAdapter.parseToModel() should be invoked exactly once for valid XML");
    }

    @Test
    void test03_orchestrationPipeline_prowideInvocationSpyProof_allFourBranches() {
        // Branch A: Schema PASS → Prowide parsing is attempted
        ProwideAdapter.resetParseToModelCallCount();
        ISOParserResult resA = ISOParser.parse(validPacs002Xml());
        assertEquals(SchemaValidationStatus.PASS, resA.getSchemaStatus());
        assertEquals(ModelParsingStatus.SUCCESS, resA.getModelStatus());
        assertEquals(1, ProwideAdapter.getParseToModelCallCount(), "Branch A: parseToModel must be called on PASS");

        // Branch B: Schema FAIL → Prowide parsing MUST NOT be attempted
        ProwideAdapter.resetParseToModelCallCount();
        ISOParserResult resB = ISOParser.parse(invalidPacs002Xml());
        assertEquals(SchemaValidationStatus.FAIL, resB.getSchemaStatus());
        assertEquals(ModelParsingStatus.SKIPPED, resB.getModelStatus());
        assertEquals(0, ProwideAdapter.getParseToModelCallCount(), "Branch B: parseToModel MUST NOT be called on FAIL");

        // Branch C: Schema NOT FOUND → Prowide parsing is allowed
        ProwideAdapter.resetParseToModelCallCount();
        ISOParserResult resC = ISOParser.parse(validCamt053Xml());
        assertEquals(SchemaValidationStatus.SCHEMA_NOT_FOUND, resC.getSchemaStatus());
        assertEquals(ModelParsingStatus.SUCCESS, resC.getModelStatus());
        assertEquals(
                1,
                ProwideAdapter.getParseToModelCallCount(),
                "Branch C: parseToModel is allowed when schema NOT FOUND");

        // Branch D: XML cannot be identified → stop appropriately
        ProwideAdapter.resetParseToModelCallCount();
        ISOParserResult resD = ISOParser.parse("<UnidentifiableRoot><SomeData/></UnidentifiableRoot>");
        assertEquals(SchemaValidationStatus.UNIDENTIFIABLE_INPUT, resD.getSchemaStatus());
        assertEquals(ModelParsingStatus.PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR, resD.getModelStatus());
        assertEquals(
                0,
                ProwideAdapter.getParseToModelCallCount(),
                "Branch D: parseToModel MUST NOT be called when XML is unidentifiable");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 4. VALIDATION ERROR QUALITY
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void test04_validationErrorQuality_preservesSeverityLineColumnMessageIdentity() {
        ISOParserResult result = ISOParser.parse(invalidPacs002Xml());

        assertEquals(SchemaValidationStatus.FAIL, result.getSchemaStatus());
        assertNotNull(result.getValidationResult());
        assertEquals("pacs.002.001.12", result.getValidationResult().getMessageId());

        List<ValidationError> errors = result.getValidationErrors();
        assertFalse(errors.isEmpty(), "Validation errors must be recorded");

        ValidationError first = errors.get(0);
        assertEquals("ERROR", first.getSeverity());
        assertNotNull(first.getMessage());
        assertFalse(first.getMessage().isBlank());
        assertTrue(first.getLine() > 0, "Line number should be positive");
        assertTrue(first.getColumn() > 0, "Column number should be positive");
        assertTrue(
                first.getMessage().contains("Max35Text") || first.getMessage().contains("maxLength"),
                "Error message should mention the violated constraint or type");
    }

    @Test
    void test04_fatalValidationError_preservesSeverityMessageLineColumn() {
        // XML with a fatal SAX syntax error (truncated before elements are closed)
        String truncatedXml =
                "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\"><FIToFIPmtStsRpt><GrpHdr><MsgId>MSGID123";

        ISOParserResult result = ISOParser.parse(truncatedXml);

        assertEquals(SchemaValidationStatus.FAIL, result.getSchemaStatus());
        assertEquals(ModelParsingStatus.SKIPPED, result.getModelStatus());
        assertNull(result.getParsedModel());

        // MUST NOT produce: Schema = FAIL, Errors = empty
        List<ValidationError> errors = result.getValidationErrors();
        assertFalse(errors.isEmpty(), "Errors must NOT be empty when XSD validation fails with a fatal SAX error");

        ValidationError fatal = errors.get(0);
        assertEquals("FATAL", fatal.getSeverity());
        assertNotNull(fatal.getMessage());
        assertFalse(fatal.getMessage().isBlank());
        assertTrue(fatal.getLine() > 0, "Fatal error line must be positive");
        assertTrue(fatal.getColumn() > 0, "Fatal error column must be positive");
    }

    @Test
    void test04_validationResult_errorsListIsUnmodifiable() {
        ISOParserResult result = ISOParser.parse(invalidPacs002Xml());
        List<ValidationError> errors = result.getValidationErrors();
        assertThrows(
                UnsupportedOperationException.class,
                () -> errors.add(new ValidationError("ERROR", "test", 1, 1)),
                "Validation errors list must be immutable");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 5. MODEL PARSING
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void test05_modelParsing_allThreeCoreBehaviors() throws IOException {
        // Behavior 1: valid XML + available model → SUCCESS
        ISOParserResult res1 = ISOParser.parse(validPacs002Xml());
        assertEquals(SchemaValidationStatus.PASS, res1.getSchemaStatus());
        assertEquals(ModelParsingStatus.SUCCESS, res1.getModelStatus());
        assertNotNull(res1.getParsedModel());
        assertEquals("MxPacs00200112", res1.getParsedModel().getClass().getSimpleName());

        // Behavior 2: valid XML + missing model → PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR
        ISOParserResult res2 = ISOParser.parse(fixture("pacs.008.001.14.xml"));
        assertEquals(SchemaValidationStatus.PASS, res2.getSchemaStatus());
        assertEquals(ModelParsingStatus.PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR, res2.getModelStatus());
        assertNull(res2.getParsedModel());

        // Behavior 3: valid XML + no configured schema + available model → schema NOT FOUND + model SUCCESS
        ISOParserResult res3 = ISOParser.parse(validCamt053Xml());
        assertEquals(SchemaValidationStatus.SCHEMA_NOT_FOUND, res3.getSchemaStatus());
        assertEquals(ModelParsingStatus.SUCCESS, res3.getModelStatus());
        assertNotNull(res3.getParsedModel());
        assertEquals("MxCamt05300108", res3.getParsedModel().getClass().getSimpleName());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 6. RESULT CONTRACT & IMPOSSIBLE COMBINATIONS
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void test06_resultContract_builderPreventsImpossibleCombinations() {
        AbstractMX dummyModel = ISOParser.parse(validPacs002Xml()).getParsedModel();
        assertNotNull(dummyModel);

        // 1. Mandatory statuses
        assertThrows(
                IllegalStateException.class,
                () -> new ISOParserResult.Builder()
                        .modelStatus(ModelParsingStatus.SUCCESS)
                        .build(),
                "Must require schemaStatus");
        assertThrows(
                IllegalStateException.class,
                () -> new ISOParserResult.Builder()
                        .schemaStatus(SchemaValidationStatus.PASS)
                        .build(),
                "Must require modelStatus");

        // 2. SCHEMA FAIL + MODEL SUCCESS is impossible
        assertThrows(
                IllegalStateException.class,
                () -> new ISOParserResult.Builder()
                        .schemaStatus(SchemaValidationStatus.FAIL)
                        .modelStatus(ModelParsingStatus.SUCCESS)
                        .parsedModel(dummyModel)
                        .build(),
                "FAIL + SUCCESS must be rejected");

        // 3. UNIDENTIFIABLE_INPUT + MODEL SUCCESS is impossible
        assertThrows(
                IllegalStateException.class,
                () -> new ISOParserResult.Builder()
                        .schemaStatus(SchemaValidationStatus.UNIDENTIFIABLE_INPUT)
                        .modelStatus(ModelParsingStatus.SUCCESS)
                        .parsedModel(dummyModel)
                        .build(),
                "UNIDENTIFIABLE + SUCCESS must be rejected");

        // 4. UNIDENTIFIABLE_INPUT + non-null identifier is impossible
        assertThrows(
                IllegalStateException.class,
                () -> new ISOParserResult.Builder()
                        .identifier(ISOMessageIdentifier.fromNamespace("pacs.002.001.12"))
                        .schemaStatus(SchemaValidationStatus.UNIDENTIFIABLE_INPUT)
                        .modelStatus(ModelParsingStatus.PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR)
                        .build(),
                "UNIDENTIFIABLE + non-null identifier must be rejected");

        // 5. MODEL SUCCESS with null model is impossible
        assertThrows(
                IllegalStateException.class,
                () -> new ISOParserResult.Builder()
                        .schemaStatus(SchemaValidationStatus.PASS)
                        .modelStatus(ModelParsingStatus.SUCCESS)
                        .parsedModel(null)
                        .build(),
                "SUCCESS + null model must be rejected");

        // 6. MODEL SKIPPED with non-null model is impossible
        assertThrows(
                IllegalStateException.class,
                () -> new ISOParserResult.Builder()
                        .schemaStatus(SchemaValidationStatus.FAIL)
                        .modelStatus(ModelParsingStatus.SKIPPED)
                        .parsedModel(dummyModel)
                        .build(),
                "SKIPPED + non-null model must be rejected");

        // 7. SCHEMA FAIL + MODEL UNAVAILABLE is impossible (must be SKIPPED)
        assertThrows(
                IllegalStateException.class,
                () -> new ISOParserResult.Builder()
                        .schemaStatus(SchemaValidationStatus.FAIL)
                        .modelStatus(ModelParsingStatus.PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR)
                        .build(),
                "FAIL + PROWIDE_MODEL_UNAVAILABLE must be rejected (must be SKIPPED)");

        // 8. SCHEMA PASS + MODEL SKIPPED is impossible
        assertThrows(
                IllegalStateException.class,
                () -> new ISOParserResult.Builder()
                        .schemaStatus(SchemaValidationStatus.PASS)
                        .modelStatus(ModelParsingStatus.SKIPPED)
                        .build(),
                "PASS + SKIPPED must be rejected");

        // 9. SCHEMA NOT FOUND + MODEL SKIPPED is impossible
        assertThrows(
                IllegalStateException.class,
                () -> new ISOParserResult.Builder()
                        .schemaStatus(SchemaValidationStatus.SCHEMA_NOT_FOUND)
                        .modelStatus(ModelParsingStatus.SKIPPED)
                        .build(),
                "SCHEMA_NOT_FOUND + SKIPPED must be rejected");

        // 10. MODEL UNAVAILABLE + non-null model is impossible
        assertThrows(
                IllegalStateException.class,
                () -> new ISOParserResult.Builder()
                        .schemaStatus(SchemaValidationStatus.PASS)
                        .modelStatus(ModelParsingStatus.PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR)
                        .parsedModel(dummyModel)
                        .build(),
                "UNAVAILABLE + non-null model must be rejected");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 7. Prowide DECOUPLING
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void test07_prowideDecoupling_classesHaveZeroProwideDependencies() {
        List<Class<?>> decoupledClasses = Arrays.asList(
                ISOMessageIdentifier.class,
                SchemaRegistry.class,
                ISOValidator.class,
                ValidationResult.class,
                ValidationError.class);

        for (Class<?> clazz : decoupledClasses) {
            // Verify fields
            for (Field field : clazz.getDeclaredFields()) {
                String typeName = field.getType().getName();
                assertFalse(
                        typeName.startsWith("com.prowidesoftware")
                                && !typeName.startsWith("com.prowidesoftware.swift.model.mx.validation"),
                        clazz.getSimpleName() + " field " + field.getName() + " references Prowide type: " + typeName);
            }
            // Verify methods (parameters and return types)
            for (Method method : clazz.getDeclaredMethods()) {
                String returnType = method.getReturnType().getName();
                assertFalse(
                        returnType.startsWith("com.prowidesoftware")
                                && !returnType.startsWith("com.prowidesoftware.swift.model.mx.validation"),
                        clazz.getSimpleName() + " method " + method.getName() + " returns Prowide type: " + returnType);
                for (Class<?> paramType : method.getParameterTypes()) {
                    String paramName = paramType.getName();
                    assertFalse(
                            paramName.startsWith("com.prowidesoftware")
                                    && !paramName.startsWith("com.prowidesoftware.swift.model.mx.validation"),
                            clazz.getSimpleName() + " method " + method.getName() + " has Prowide parameter: "
                                    + paramName);
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 8. MULTIPLE MESSAGE TYPES THROUGH SINGLE ENTRY POINT
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void test08_multipleMessageTypes_sameStaticEntryPointWithoutBranching() {
        // pacs
        ISOParserResult r1 = ISOParser.parse(validPacs002Xml());
        assertEquals("pacs", r1.getIdentifier().getBusinessArea());
        assertEquals("pacs.002.001.12", r1.getIdentifier().getFullMessageType());

        // camt
        ISOParserResult r2 = ISOParser.parse(validCamt053Xml());
        assertEquals("camt", r2.getIdentifier().getBusinessArea());
        assertEquals("camt.053.001.08", r2.getIdentifier().getFullMessageType());

        // seev
        ISOParserResult r3 = ISOParser.parse(validSeev031Xml());
        assertEquals("seev", r3.getIdentifier().getBusinessArea());
        assertEquals("seev.031.001.09", r3.getIdentifier().getFullMessageType());

        // pain
        String painXml = "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pain.001.001.09\">"
                + "<CstmrCdtTrfInitn><GrpHdr><MsgId>M1</MsgId><CreDtTm>2023-10-01T12:00:00Z</CreDtTm>"
                + "<NbOfTxs>1</NbOfTxs><InitgPty><Nm>Acme</Nm></InitgPty></GrpHdr>"
                + "<PmtInf><PmtInfId>P1</PmtInfId><PmtMtd>TRF</PmtMtd>"
                + "<ReqdExctnDt><Dt>2023-10-02</Dt></ReqdExctnDt>"
                + "<Dbtr><Nm>Dbtr</Nm></Dbtr><DbtrAcct><Id><Othr><Id>123</Id></Othr></Id></DbtrAcct>"
                + "<DbtrAgt><FinInstnId><BICFI>BOFAUS3N</BICFI></FinInstnId></DbtrAgt>"
                + "<CdtTrfTxInf><PmtId><EndToEndId>E1</EndToEndId></PmtId>"
                + "<Amt><InstdAmt Ccy=\"USD\">10.00</InstdAmt></Amt>"
                + "<Cdtr><Nm>Cdtr</Nm></Cdtr><CdtrAcct><Id><Othr><Id>456</Id></Othr></Id></CdtrAcct>"
                + "</CdtTrfTxInf></PmtInf></CstmrCdtTrfInitn></Document>";
        ISOParserResult r4 = ISOParser.parse(painXml);
        assertEquals("pain", r4.getIdentifier().getBusinessArea());
        assertEquals("pain.001.001.09", r4.getIdentifier().getFullMessageType());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // 9. REAL SCHEMA + MODEL ALIGNMENT (GOLDEN TEST)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void test09_goldenEndToEnd_realXmlRealSchemaRealModel() {
        // XML + official XSD + generated Prowide MxPacs00200112 all align for pacs.002.001.12
        ISOParserResult result = ISOParser.parse(validPacs002Xml());

        // Full validation pass
        assertEquals(SchemaValidationStatus.PASS, result.getSchemaStatus());
        assertTrue(result.isSchemaValid());
        assertTrue(result.getValidationErrors().isEmpty());

        // Full model pass
        assertEquals(ModelParsingStatus.SUCCESS, result.getModelStatus());
        assertTrue(result.isModelParsed());
        assertNotNull(result.getParsedModel());
        assertTrue(
                result.getParsedModel() instanceof MxPacs00200112,
                "Parsed model must be instance of generated MxPacs00200112");

        MxPacs00200112 pacsModel = (MxPacs00200112) result.getParsedModel();
        assertNotNull(pacsModel.getFIToFIPmtStsRpt());
        assertNotNull(pacsModel.getFIToFIPmtStsRpt().getGrpHdr());
        assertEquals("MSGID123", pacsModel.getFIToFIPmtStsRpt().getGrpHdr().getMsgId());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private static String validPacs002Xml() {
        return "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\">"
                + "<FIToFIPmtStsRpt><GrpHdr><MsgId>MSGID123</MsgId>"
                + "<CreDtTm>2023-10-01T12:00:00Z</CreDtTm></GrpHdr>"
                + "<TxInfAndSts><OrgnlInstrId>INSTR123</OrgnlInstrId><TxSts>ACCP</TxSts>"
                + "</TxInfAndSts></FIToFIPmtStsRpt></Document>";
    }

    private static String invalidPacs002Xml() {
        // MsgId is 43 characters; pacs.002.001.12.xsd defines MsgId as Max35Text
        return "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\">"
                + "<FIToFIPmtStsRpt><GrpHdr><MsgId>MSGID1234567890123456789012345678901234567</MsgId>"
                + "<CreDtTm>2023-10-01T12:00:00Z</CreDtTm></GrpHdr>"
                + "<TxInfAndSts><OrgnlInstrId>INSTR123</OrgnlInstrId><TxSts>ACCP</TxSts>"
                + "</TxInfAndSts></FIToFIPmtStsRpt></Document>";
    }

    private static String validCamt053Xml() {
        return "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.053.001.08\">"
                + "<BkToCstmrStmt><GrpHdr><MsgId>MSG-1</MsgId>"
                + "<CreDtTm>2023-10-01T12:00:00Z</CreDtTm></GrpHdr></BkToCstmrStmt></Document>";
    }

    private static String validSeev031Xml() {
        return "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:seev.031.001.09\">"
                + "<CorpActnNtfctn><NtfctnGnlInf>"
                + "<NtfctnId>NTF-1</NtfctnId>"
                + "<NtfctnTp>NEWM</NtfctnTp>"
                + "</NtfctnGnlInf></CorpActnNtfctn></Document>";
    }

    private static String fixture(String name) throws IOException {
        try (InputStream is = ISOParserHardeningAuditTest.class.getResourceAsStream("/" + name)) {
            assertNotNull(is, "Missing test fixture: " + name);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
