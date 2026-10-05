package com.prowidesoftware.swift.model.mx;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.prowidesoftware.swift.model.mx.dic.FIToFIPaymentStatusReportV12;
import com.prowidesoftware.swift.model.mx.dic.PaymentTransaction130;
import com.prowidesoftware.swift.model.mx.dic.StatusReason6Choice;
import com.prowidesoftware.swift.model.mx.dic.StatusReasonInformation12;
import com.prowidesoftware.swift.model.mx.validation.ISOParser;
import com.prowidesoftware.swift.model.mx.validation.ISOParserResult;
import com.prowidesoftware.swift.model.mx.validation.semantic.*;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SpELSemanticValidationTest {

    private SemanticRuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new SemanticRuleEngine(new SpELRuleEvaluator());

        // 1. Load rule from JSON
        Gson gson = new Gson();
        SemanticRuleDefinition rule = gson.fromJson(
                new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/semantic/rule.json"))),
                SemanticRuleDefinition.class);
        engine.addRule(rule);
    }

    private String validXml =
            "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\">\n" + "    <FIToFIPmtStsRpt>\n"
                    + "        <GrpHdr>\n"
                    + "            <MsgId>MSGID123</MsgId>\n"
                    + "            <CreDtTm>2023-10-01T12:00:00Z</CreDtTm>\n"
                    + "        </GrpHdr>\n"
                    + "        <TxInfAndSts>\n"
                    + "            <OrgnlInstrId>INSTR123</OrgnlInstrId>\n"
                    + "            <TxSts>RJCT</TxSts>\n"
                    + "            <StsRsnInf><Rsn><Cd>AG01</Cd></Rsn></StsRsnInf>\n"
                    + "        </TxInfAndSts>\n"
                    + "    </FIToFIPmtStsRpt>\n"
                    + "</Document>";

    private String xsdPassSemanticFailXml =
            "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\">\n" + "    <FIToFIPmtStsRpt>\n"
                    + "        <GrpHdr>\n"
                    + "            <MsgId>MSGID123</MsgId>\n"
                    + "            <CreDtTm>2023-10-01T12:00:00Z</CreDtTm>\n"
                    + "        </GrpHdr>\n"
                    + "        <TxInfAndSts>\n"
                    + "            <OrgnlInstrId>INSTR123</OrgnlInstrId>\n"
                    + "            <TxSts>RJCT</TxSts>\n"
                    + "            <!-- StsRsnInf is omitted -->\n"
                    + "        </TxInfAndSts>\n"
                    + "    </FIToFIPmtStsRpt>\n"
                    + "</Document>";

    @Test
    void testValidSemanticPass() {
        ISOParserResult res = ISOParser.parse(validXml);
        assertEquals(ISOParserResult.SchemaValidationStatus.PASS, res.getSchemaStatus());

        SemanticValidationResult semRes = engine.validate(res.getParsedModel());
        if (semRes.hasFailures()) {
            System.out.println("DEBUG FAILURE: " + semRes.getViolations().get(0).getMessage());
        }
        assertFalse(semRes.hasFailures(), "Semantic rule should pass");
    }

    @Test
    void testXsdPassSemanticFail() {
        ISOParserResult res = ISOParser.parse(xsdPassSemanticFailXml);
        assertEquals(ISOParserResult.SchemaValidationStatus.PASS, res.getSchemaStatus());

        SemanticValidationResult semRes = engine.validate(res.getParsedModel());
        assertTrue(semRes.hasFailures(), "Semantic rule should fail");

        SemanticViolation violation = semRes.getViolations().get(0);
        assertEquals("CBPR_PACS002_01", violation.getRuleId());
        assertEquals("FATAL", violation.getSeverity());
        assertEquals("FIToFIPmtStsRpt/TxInfAndSts/StsRsnInf", violation.getErrorPath());
    }

    @Test
    void testMissingOptionalFieldSafety() {
        MxPacs00200112 model = new MxPacs00200112();
        model.setFIToFIPmtStsRpt(null); // Missing block entirely

        SemanticValidationResult semRes = engine.validate(model);
        assertFalse(semRes.hasFailures(), "Should safely handle missing block without NPE");
    }

    @Test
    void testEmptyCollectionSafety() {
        MxPacs00200112 model = new MxPacs00200112();
        model.setFIToFIPmtStsRpt(new FIToFIPaymentStatusReportV12()); // TxInfAndSts is empty list

        SemanticValidationResult semRes = engine.validate(model);
        assertFalse(semRes.hasFailures(), "Should safely handle empty collection");
    }

    @Test
    void testMultipleCollectionEntries() {
        MxPacs00200112 model = new MxPacs00200112();
        FIToFIPaymentStatusReportV12 rpt = new FIToFIPaymentStatusReportV12();

        // Pass entry
        PaymentTransaction130 tx1 = new PaymentTransaction130();
        tx1.setTxSts("RJCT");
        StatusReasonInformation12 rsn = new StatusReasonInformation12();
        rsn.setRsn(new StatusReason6Choice().setCd("AG01"));
        tx1.addStsRsnInf(rsn);

        // Fail entry
        PaymentTransaction130 tx2 = new PaymentTransaction130();
        tx2.setTxSts("RJCT"); // missing StsRsnInf

        rpt.addTxInfAndSts(tx1);
        rpt.addTxInfAndSts(tx2);
        model.setFIToFIPmtStsRpt(rpt);

        SemanticValidationResult semRes = engine.validate(model);
        assertTrue(semRes.hasFailures(), "Should fail because tx2 violates the rule");
    }

    @Test
    void testSecurity_ArbitraryMethodInvocationBlocked() {
        SemanticRuleDefinition badRule = new SemanticRuleDefinition();
        badRule.setMessageType("pacs.002");
        badRule.setRuleId("MALICIOUS_1");
        // Attempt to execute a system command
        badRule.setExpression("T(java.lang.Runtime).getRuntime().exec('echo hacked') == null");

        badRule.setSeverity("FATAL");
        badRule.setErrorPath("Path");
        SemanticRuleEngine badEngine = new SemanticRuleEngine(new SpELRuleEvaluator());
        badEngine.addRule(badRule);

        SemanticValidationResult res = badEngine.validate(new MxPacs00200112());
        assertTrue(res.hasFailures());
        assertTrue(res.getTechnicalErrors().get(0).getErrorMessage().contains("Type cannot be found"));
    }

    @Test
    void testSecurity_ClassAccessBlocked() {
        SemanticRuleDefinition badRule = new SemanticRuleDefinition();
        badRule.setMessageType("pacs.002");
        badRule.setRuleId("MALICIOUS_2");
        // Attempt to read class information
        badRule.setExpression("class.name == 'com.prowidesoftware.swift.model.mx.MxPacs00200112'");

        badRule.setSeverity("FATAL");
        badRule.setErrorPath("Path");
        SemanticRuleEngine badEngine = new SemanticRuleEngine(new SpELRuleEvaluator());
        badEngine.addRule(badRule);

        SemanticValidationResult res = badEngine.validate(new MxPacs00200112());
        assertTrue(res.hasFailures());
        // SimpleEvaluationContext prevents accessing 'class' property
        assertTrue(res.getTechnicalErrors()
                .get(0)
                .getErrorMessage()
                .contains("Property or field 'class' cannot be found"));
    }

    public static class DummyMX extends com.prowidesoftware.swift.model.mx.AbstractMX {
        private java.math.BigDecimal amt;
        private java.time.OffsetDateTime dt;

        public DummyMX(java.math.BigDecimal amt, java.time.OffsetDateTime dt) {
            super();
            this.amt = amt;
            this.dt = dt;
        }

        public java.math.BigDecimal getAmt() {
            return amt;
        }

        public java.time.OffsetDateTime getDt() {
            return dt;
        }

        @Override
        public String getBusinessProcess() {
            return "test";
        }

        @Override
        public int getFunctionality() {
            return 1;
        }

        @Override
        public int getVariant() {
            return 1;
        }

        @Override
        public int getVersion() {
            return 1;
        }

        @Override
        public String getNamespace() {
            return "urn:iso:std:iso:20022:tech:xsd:test.001.001.01";
        }

        @Override
        @SuppressWarnings("rawtypes")
        public Class[] getClasses() {
            return new Class[0];
        }
    }

    @Test
    void testDateAndNumericComparisons() throws Exception {
        SpELRuleEvaluator eval = new SpELRuleEvaluator();
        SemanticRuleEngine engine = new SemanticRuleEngine(eval);

        SemanticRuleDefinition numericRule = new SemanticRuleDefinition();
        numericRule.setRuleId("NUM_1");
        numericRule.setMessageType("test.001");
        numericRule.setSeverity("FATAL");
        numericRule.setErrorPath("Amt");
        numericRule.setExpression("amt > 1000.0");

        SemanticRuleDefinition dateRule = new SemanticRuleDefinition();
        dateRule.setRuleId("DATE_1");
        dateRule.setMessageType("test.001");
        dateRule.setSeverity("FATAL");
        dateRule.setErrorPath("Dt");
        dateRule.setExpression("dt > '2023-01-01T00:00:00Z'");

        engine.addRule(numericRule);
        engine.addRule(dateRule);

        DummyMX model = new DummyMX(new BigDecimal("1500.50"), OffsetDateTime.parse("2023-10-01T12:00:00Z"));

        SemanticValidationResult semRes = engine.validate(model);

        // Numeric rule passed successfully (no violation or tech error)
        boolean hasNumViolation =
                semRes.getViolations().stream().anyMatch(v -> v.getRuleId().equals("NUM_1"));
        boolean hasNumError =
                semRes.getTechnicalErrors().stream().anyMatch(e -> e.getRuleId().equals("NUM_1"));
        assertFalse(hasNumViolation);
        assertFalse(hasNumError);

        // Date rule triggers a technical evaluation error because SpEL cannot seamlessly compare OffsetDateTime with
        // String natively
        boolean hasDateError =
                semRes.getTechnicalErrors().stream().anyMatch(e -> e.getRuleId().equals("DATE_1"));
        assertTrue(hasDateError, "SpEL natively fails on OffsetDateTime comparison without TypeConverter");
    }

    @Test
    void testMalformedRuleDefinition() {
        SemanticRuleEngine engine = new SemanticRuleEngine(new SpELRuleEvaluator());
        SemanticRuleDefinition badRule = new SemanticRuleDefinition();

        try {
            engine.addRule(badRule);
            org.junit.jupiter.api.Assertions.fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("ruleId is required"));
        }
    }
}
