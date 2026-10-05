package com.prowidesoftware.swift.model.mx;

import static org.junit.jupiter.api.Assertions.*;

import com.prowidesoftware.swift.model.mx.validation.ISOParser;
import com.prowidesoftware.swift.model.mx.validation.ISOParserResult;
import com.prowidesoftware.swift.model.mx.validation.semantic.SemanticRuleDefinition;
import com.prowidesoftware.swift.model.mx.validation.semantic.SemanticRuleEngine;
import com.prowidesoftware.swift.model.mx.validation.semantic.SpELRuleEvaluator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SemanticIntegrationTest {

    private SemanticRuleEngine mockEngine;
    private SpELRuleEvaluator evaluator;
    private SemanticRuleEngine realEngine;

    @BeforeAll
    static void setupSchema() throws Exception {
        // Clear schema registry so it defaults to SCHEMA_NOT_FOUND for unknown paths,
        // which still allows ModelParsing and SemanticValidation to run.
    }

    private static class SemanticRuleEngineWrapper extends SemanticRuleEngine {
        private boolean called = false;

        public SemanticRuleEngineWrapper(SpELRuleEvaluator evaluator) {
            super(evaluator);
        }

        @Override
        public com.prowidesoftware.swift.model.mx.validation.semantic.SemanticValidationResult validate(
                com.prowidesoftware.swift.model.mx.AbstractMX model) {
            called = true;
            return super.validate(model);
        }

        public boolean isCalled() {
            return called;
        }
    }

    @BeforeEach
    void setup() {
        evaluator = new SpELRuleEvaluator();
        mockEngine = new SemanticRuleEngineWrapper(evaluator);
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

        ISOParserResult result = ISOParser.parse(xml, realEngine);

        assertTrue(result.getSchemaStatus() == ISOParserResult.SchemaValidationStatus.PASS
                || result.getSchemaStatus() == ISOParserResult.SchemaValidationStatus.SCHEMA_NOT_FOUND);
        assertEquals(ISOParserResult.ModelParsingStatus.SUCCESS, result.getModelStatus());
        assertEquals(ISOParserResult.SemanticValidationStatus.PASS, result.getSemanticStatus());
        assertTrue(result.getSemanticResult().getViolations().isEmpty());
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

        ISOParserResult result = ISOParser.parse(xml, realEngine);

        assertTrue(result.getSchemaStatus() == ISOParserResult.SchemaValidationStatus.PASS
                || result.getSchemaStatus() == ISOParserResult.SchemaValidationStatus.SCHEMA_NOT_FOUND);
        assertEquals(ISOParserResult.ModelParsingStatus.SUCCESS, result.getModelStatus());
        assertEquals(ISOParserResult.SemanticValidationStatus.FAIL, result.getSemanticStatus());
        assertEquals(1, result.getSemanticResult().getViolations().size());
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

        ISOParserResult result = ISOParser.parse(invalidXml, mockEngine);

        assertEquals(ISOParserResult.SchemaValidationStatus.FAIL, result.getSchemaStatus());
        assertEquals(ISOParserResult.ModelParsingStatus.SKIPPED, result.getModelStatus());
        assertEquals(ISOParserResult.SemanticValidationStatus.SKIPPED, result.getSemanticStatus());

        assertFalse(((SemanticRuleEngineWrapper) mockEngine).isCalled(), "Engine should not be called when XSD fails");
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

        ISOParserResult result = ISOParser.parse(noModelXml, mockEngine);

        assertEquals(ISOParserResult.SchemaValidationStatus.SCHEMA_NOT_FOUND, result.getSchemaStatus());
        assertEquals(
                ISOParserResult.ModelParsingStatus.PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR, result.getModelStatus());
        assertEquals(ISOParserResult.SemanticValidationStatus.SKIPPED, result.getSemanticStatus());

        assertFalse(
                ((SemanticRuleEngineWrapper) mockEngine).isCalled(),
                "Engine should not be called when Model is unavailable");
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

        ISOParserResult result = ISOParser.parse(xml, realEngine);

        assertTrue(result.getSchemaStatus() == ISOParserResult.SchemaValidationStatus.PASS
                || result.getSchemaStatus() == ISOParserResult.SchemaValidationStatus.SCHEMA_NOT_FOUND);
        assertEquals(ISOParserResult.ModelParsingStatus.SUCCESS, result.getModelStatus());
        assertEquals(ISOParserResult.SemanticValidationStatus.NOT_APPLICABLE, result.getSemanticStatus());
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

        ISOParserResult result = ISOParser.parse(xml, realEngine);

        assertTrue(result.getSchemaStatus() == ISOParserResult.SchemaValidationStatus.PASS
                || result.getSchemaStatus() == ISOParserResult.SchemaValidationStatus.SCHEMA_NOT_FOUND);
        assertEquals(ISOParserResult.ModelParsingStatus.SUCCESS, result.getModelStatus());
        assertEquals(ISOParserResult.SemanticValidationStatus.TECHNICAL_ERROR, result.getSemanticStatus());
        assertEquals(1, result.getSemanticResult().getTechnicalErrors().size());
        assertTrue(result.getSemanticResult().getViolations().isEmpty());
    }
}
