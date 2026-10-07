package com.prowidesoftware.swift.model.mx;

import static org.junit.jupiter.api.Assertions.*;

import com.prowidesoftware.swift.model.mx.validation.ISOParser;
import com.prowidesoftware.swift.model.mx.validation.ISOParserResult;
import com.prowidesoftware.swift.model.mx.validation.semantic.SemanticRuleDefinition;
import com.prowidesoftware.swift.model.mx.validation.semantic.SemanticRuleEngine;
import com.prowidesoftware.swift.model.mx.validation.semantic.SemanticValidationResult;
import com.prowidesoftware.swift.model.mx.validation.semantic.SpELRuleEvaluator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SemanticIntegrationTest {

    private SpELRuleEvaluator evaluator;
    private SemanticRuleEngine realEngine;

    @BeforeAll
    static void setupSchema() throws Exception {
        // Clear schema registry so it defaults to SCHEMA_NOT_FOUND for unknown paths,
        // which still allows ModelParsing and SemanticValidation to run.
    }

    @BeforeEach
    void setup() {
        evaluator = new SpELRuleEvaluator();
        realEngine = new SemanticRuleEngine(evaluator);
    }

    @Test
    void testXsdPass_ModelSuccess_SemanticPass() throws Exception {
        SemanticRuleDefinition rule = new SemanticRuleDefinition();
        rule.setRuleId("TEST_1");
        rule.setMessageType("pacs.002");
        rule.setSeverity("FATAL");
        rule.setErrorPath("GrpHdr/MsgId");
        // MsgId is "MSG123" in the XML, so == 'MSG123' is true -> PASS
        rule.setExpression("FIToFIPmtStsRpt?.grpHdr?.msgId == 'MSG123'");

        realEngine.addRule(rule);

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\">\n"
                + "    <FIToFIPmtStsRpt>\n"
                + "        <GrpHdr>\n"
                + "            <MsgId>MSG123</MsgId>\n"
                + "            <CreDtTm>2023-10-01T12:00:00Z</CreDtTm>\n"
                + "        </GrpHdr>\n"
                + "    </FIToFIPmtStsRpt>\n"
                + "</Document>";

        ISOParserResult result = ISOParser.parse(xml);

        assertTrue(result.getSchemaStatus() == ISOParserResult.SchemaValidationStatus.PASS
                || result.getSchemaStatus() == ISOParserResult.SchemaValidationStatus.SCHEMA_NOT_FOUND);
        assertEquals(ISOParserResult.ModelParsingStatus.SUCCESS, result.getModelStatus());

        SemanticValidationResult semResult = realEngine.validate(result.getParsedModel());
        assertTrue(semResult.getViolations().isEmpty());
        assertTrue(semResult.getTechnicalErrors().isEmpty());
        assertEquals(1, semResult.getEvaluatedRuleCount());
    }

    @Test
    void testXsdPass_ModelSuccess_SemanticFail() throws Exception {
        SemanticRuleDefinition rule = new SemanticRuleDefinition();
        rule.setRuleId("TEST_2");
        rule.setMessageType("pacs.002");
        rule.setSeverity("FATAL");
        rule.setErrorPath("GrpHdr/MsgId");
        // Require MsgId to be 'VALID' (it is 'MSG123') -> FAIL
        rule.setExpression("FIToFIPmtStsRpt?.grpHdr?.msgId == 'VALID'");

        realEngine.addRule(rule);

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\">\n"
                + "    <FIToFIPmtStsRpt>\n"
                + "        <GrpHdr>\n"
                + "            <MsgId>MSG123</MsgId>\n"
                + "            <CreDtTm>2023-10-01T12:00:00Z</CreDtTm>\n"
                + "        </GrpHdr>\n"
                + "    </FIToFIPmtStsRpt>\n"
                + "</Document>";

        ISOParserResult result = ISOParser.parse(xml);

        assertTrue(result.getSchemaStatus() == ISOParserResult.SchemaValidationStatus.PASS
                || result.getSchemaStatus() == ISOParserResult.SchemaValidationStatus.SCHEMA_NOT_FOUND);
        assertEquals(ISOParserResult.ModelParsingStatus.SUCCESS, result.getModelStatus());

        SemanticValidationResult semResult = realEngine.validate(result.getParsedModel());
        assertEquals(1, semResult.getViolations().size());
        assertTrue(semResult.hasFailures());
    }

    @Test
    void testXsdFail_SemanticNotInvoked() throws Exception {
        String invalidXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\">\n"
                + "    <FIToFIPmtStsRpt>\n"
                + "        <GrpHdr>\n"
                + "        </GrpHdr>\n"
                + // Missing required MsgId and CreDtTm
                "    </FIToFIPmtStsRpt>\n"
                + "</Document>";

        ISOParserResult result = ISOParser.parse(invalidXml);

        assertEquals(ISOParserResult.SchemaValidationStatus.FAIL, result.getSchemaStatus());
        assertEquals(ISOParserResult.ModelParsingStatus.SKIPPED, result.getModelStatus());
        assertFalse(result.isModelParsed());
    }

    @Test
    void testModelUnavailable_SemanticNotInvoked() throws Exception {
        // For testing Model Unavailable, we just use a schema that doesn't exist.
        // It will return SCHEMA_NOT_FOUND, meaning Model Parsing is attempted but Prowide will return null.
        String noModelXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.999.001.01\">\n"
                + "    <Fake>\n"
                + "    </Fake>\n"
                + "</Document>";

        ISOParserResult result = ISOParser.parse(noModelXml);

        assertEquals(ISOParserResult.SchemaValidationStatus.SCHEMA_NOT_FOUND, result.getSchemaStatus());
        assertEquals(
                ISOParserResult.ModelParsingStatus.PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR, result.getModelStatus());
        assertFalse(result.isModelParsed());
    }

    @Test
    void testNoApplicableSemanticRules() throws Exception {
        // No rules added to realEngine
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\">\n"
                + "    <FIToFIPmtStsRpt>\n"
                + "        <GrpHdr>\n"
                + "            <MsgId>MSG123</MsgId>\n"
                + "            <CreDtTm>2023-10-01T12:00:00Z</CreDtTm>\n"
                + "        </GrpHdr>\n"
                + "    </FIToFIPmtStsRpt>\n"
                + "</Document>";

        ISOParserResult result = ISOParser.parse(xml);

        assertTrue(result.getSchemaStatus() == ISOParserResult.SchemaValidationStatus.PASS
                || result.getSchemaStatus() == ISOParserResult.SchemaValidationStatus.SCHEMA_NOT_FOUND);
        assertEquals(ISOParserResult.ModelParsingStatus.SUCCESS, result.getModelStatus());

        SemanticValidationResult semResult = realEngine.validate(result.getParsedModel());
        assertEquals(0, semResult.getEvaluatedRuleCount());
        assertFalse(semResult.hasFailures());
    }

    @Test
    void testTechnicalRuleEvaluationFailure() throws Exception {
        SemanticRuleDefinition rule = new SemanticRuleDefinition();
        rule.setRuleId("TECH_1");
        rule.setMessageType("pacs.002");
        rule.setSeverity("FATAL");
        rule.setErrorPath("GrpHdr/MsgId");
        // Malformed SpEL -> causes evaluation error
        rule.setExpression("T(java.lang.Runtime).getRuntime()");

        realEngine.addRule(rule);

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\">\n"
                + "    <FIToFIPmtStsRpt>\n"
                + "        <GrpHdr>\n"
                + "            <MsgId>MSG123</MsgId>\n"
                + "            <CreDtTm>2023-10-01T12:00:00Z</CreDtTm>\n"
                + "        </GrpHdr>\n"
                + "    </FIToFIPmtStsRpt>\n"
                + "</Document>";

        ISOParserResult result = ISOParser.parse(xml);

        assertTrue(result.getSchemaStatus() == ISOParserResult.SchemaValidationStatus.PASS
                || result.getSchemaStatus() == ISOParserResult.SchemaValidationStatus.SCHEMA_NOT_FOUND);
        assertEquals(ISOParserResult.ModelParsingStatus.SUCCESS, result.getModelStatus());

        SemanticValidationResult semResult = realEngine.validate(result.getParsedModel());
        assertEquals(1, semResult.getTechnicalErrors().size());
        assertTrue(semResult.getViolations().isEmpty());
    }

    @Test
    void testVersionApplicability_Skipped() throws Exception {
        SemanticRuleDefinition rule = new SemanticRuleDefinition();
        rule.setRuleId("VERS_1");
        rule.setMessageType("pacs.002");
        // Only applicable for version 99, but we pass version 12
        rule.setVersions(java.util.Collections.singletonList("99"));
        rule.setSeverity("FATAL");
        rule.setErrorPath("GrpHdr");
        rule.setExpression("false"); // Always fails if evaluated
        realEngine.addRule(rule);

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\">\n"
                + "    <FIToFIPmtStsRpt>\n"
                + "        <GrpHdr>\n"
                + "            <MsgId>MSG123</MsgId>\n"
                + "            <CreDtTm>2023-10-01T12:00:00Z</CreDtTm>\n"
                + "        </GrpHdr>\n"
                + "    </FIToFIPmtStsRpt>\n"
                + "</Document>";

        ISOParserResult result = ISOParser.parse(xml);
        SemanticValidationResult semResult = realEngine.validate(result.getParsedModel());
        assertEquals(0, semResult.getEvaluatedRuleCount(), "Rule should not be evaluated due to version mismatch");
    }

    @Test
    void testVersionApplicability_Matched() throws Exception {
        SemanticRuleDefinition rule = new SemanticRuleDefinition();
        rule.setRuleId("VERS_2");
        rule.setMessageType("pacs.002");
        rule.setVersions(java.util.Collections.singletonList("12"));
        rule.setSeverity("FATAL");
        rule.setErrorPath("GrpHdr");
        rule.setExpression("FIToFIPmtStsRpt?.grpHdr?.msgId == 'MSG123'"); // evaluates to true (PASS)
        realEngine.addRule(rule);

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\">\n"
                + "    <FIToFIPmtStsRpt>\n"
                + "        <GrpHdr>\n"
                + "            <MsgId>MSG123</MsgId>\n"
                + "            <CreDtTm>2023-10-01T12:00:00Z</CreDtTm>\n"
                + "        </GrpHdr>\n"
                + "    </FIToFIPmtStsRpt>\n"
                + "</Document>";

        ISOParserResult result = ISOParser.parse(xml);
        SemanticValidationResult semResult = realEngine.validate(result.getParsedModel());
        assertEquals(1, semResult.getEvaluatedRuleCount(), "Rule should be evaluated because version matches");
    }

    @Test
    void testMultiMessage_Camt() throws Exception {
        SemanticRuleDefinition rule = new SemanticRuleDefinition();
        rule.setRuleId("CAMT_1");
        rule.setMessageType("camt.053");
        rule.setSeverity("FATAL");
        rule.setErrorPath("BkToCstmrStmt");
        rule.setExpression("bkToCstmrStmt?.grpHdr?.msgId == 'SDFSDF'");
        realEngine.addRule(rule);

        String xml = new String(
                java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/test/resources/camt.053.001.07.xml")));
        ISOParserResult result = ISOParser.parse(xml);

        assertEquals(ISOParserResult.ModelParsingStatus.SUCCESS, result.getModelStatus());
        SemanticValidationResult semResult = realEngine.validate(result.getParsedModel());
        assertEquals(1, semResult.getEvaluatedRuleCount());
        assertFalse(semResult.hasFailures());
    }

    @Test
    void testMultiMessage_Seev() throws Exception {
        SemanticRuleDefinition rule = new SemanticRuleDefinition();
        rule.setRuleId("SEEV_1");
        rule.setMessageType("seev.031");
        rule.setSeverity("FATAL");
        rule.setErrorPath("CorpActnNtfctn");
        rule.setExpression("corpActnNtfctn?.corpActnGnlInf?.corpActnEvtId == '111111111'");
        realEngine.addRule(rule);

        String xml = new String(
                java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/test/resources/seev.031.002.09.xml")));
        ISOParserResult result = ISOParser.parse(xml);

        assertEquals(ISOParserResult.ModelParsingStatus.SUCCESS, result.getModelStatus());
        SemanticValidationResult semResult = realEngine.validate(result.getParsedModel());
        assertEquals(1, semResult.getEvaluatedRuleCount());
        assertFalse(semResult.hasFailures());
    }
}
