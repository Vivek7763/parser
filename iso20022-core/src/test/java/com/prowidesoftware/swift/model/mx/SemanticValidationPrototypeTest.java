package com.prowidesoftware.swift.model.mx;

import static org.junit.jupiter.api.Assertions.*;

import com.prowidesoftware.swift.model.mx.dic.FIToFIPaymentStatusReportV12;
import com.prowidesoftware.swift.model.mx.dic.PaymentTransaction130;
import com.prowidesoftware.swift.model.mx.validation.ISOParser;
import com.prowidesoftware.swift.model.mx.validation.ISOParserResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class SemanticValidationPrototypeTest {

    // --- GENERIC SEMANTIC ABSTRACTION ---

    public interface RuleContext {
        // Future context holding variables, errors, scheme metadata
    }

    public static class SemanticValidationResult {
        private final List<SemanticViolation> violations = new ArrayList<>();

        public void addViolation(SemanticViolation v) {
            violations.add(v);
        }

        public boolean hasFailures() {
            return !violations.isEmpty();
        }

        public List<SemanticViolation> getViolations() {
            return violations;
        }
    }

    public static class SemanticViolation {
        public final String ruleId;
        public final String severity;
        public final String message;

        public SemanticViolation(String ruleId, String severity, String message) {
            this.ruleId = ruleId;
            this.severity = severity;
            this.message = message;
        }
    }

    public interface SemanticRule {
        String getRuleId();

        String getSeverity();

        String getMessage();

        boolean evaluate(AbstractMX model, RuleContext context);
    }

    public static class SemanticRuleEngine {
        private final List<SemanticRule> rules = new ArrayList<>();

        public void addRule(SemanticRule rule) {
            rules.add(rule);
        }

        public SemanticValidationResult evaluate(AbstractMX model, RuleContext context) {
            SemanticValidationResult result = new SemanticValidationResult();
            for (SemanticRule rule : rules) {
                if (!rule.evaluate(model, context)) {
                    result.addViolation(new SemanticViolation(rule.getRuleId(), rule.getSeverity(), rule.getMessage()));
                }
            }
            return result;
        }
    }

    // --- PROTOTYPE RULE ---

    /**
     * Rule: If TxSts == 'RJCT', StsRsnInf must be present and not empty.
     */
    public static class Pacs002RejectReasonRule implements SemanticRule {
        @Override
        public String getRuleId() {
            return "CBPR_PACS002_01";
        }

        @Override
        public String getSeverity() {
            return "FATAL";
        }

        @Override
        public String getMessage() {
            return "Status Reason Information is mandatory when Transaction Status is RJCT.";
        }

        @Override
        public boolean evaluate(AbstractMX model, RuleContext context) {
            if (!(model instanceof MxPacs00200112)) return true; // Skip if wrong model

            MxPacs00200112 pacs002 = (MxPacs00200112) model;
            FIToFIPaymentStatusReportV12 rpt = pacs002.getFIToFIPmtStsRpt();
            if (rpt == null) return true; // Null check

            // JAXB guarantees getTxInfAndSts() returns empty list, never null
            for (PaymentTransaction130 tx : rpt.getTxInfAndSts()) {
                if ("RJCT".equals(tx.getTxSts())) {
                    if (tx.getStsRsnInf() == null || tx.getStsRsnInf().isEmpty()) {
                        return false; // Rule failed
                    }
                }
            }
            return true; // Passed
        }
    }

    // --- TESTS ---

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
                    + "            <!-- StsRsnInf is omitted. XSD allows it (minOccurs=0), but Semantic Rule mandates it if TxSts=RJCT -->\n"
                    + "        </TxInfAndSts>\n"
                    + "    </FIToFIPmtStsRpt>\n"
                    + "</Document>";

    @Test
    void testSemanticPass() throws Exception {
        ISOParserResult res = ISOParser.parse(validXml);
        assertEquals(ISOParserResult.SchemaValidationStatus.PASS, res.getSchemaStatus());
        assertNotNull(res.getParsedModel());

        SemanticRuleEngine engine = new SemanticRuleEngine();
        engine.addRule(new Pacs002RejectReasonRule());

        SemanticValidationResult semRes = engine.evaluate(res.getParsedModel(), new RuleContext() {});
        assertFalse(semRes.hasFailures());
    }

    @Test
    void testSemanticFail_WhileXsdPasses() throws Exception {
        ISOParserResult res = ISOParser.parse(xsdPassSemanticFailXml);
        // PROOF: XSD completely passes this payload!
        assertEquals(ISOParserResult.SchemaValidationStatus.PASS, res.getSchemaStatus());
        assertNotNull(res.getParsedModel());

        SemanticRuleEngine engine = new SemanticRuleEngine();
        engine.addRule(new Pacs002RejectReasonRule());

        SemanticValidationResult semRes = engine.evaluate(res.getParsedModel(), new RuleContext() {});

        // PROOF: Semantic validation correctly catches the business logic violation
        assertTrue(semRes.hasFailures());
        assertEquals(1, semRes.getViolations().size());
        assertEquals("CBPR_PACS002_01", semRes.getViolations().get(0).ruleId);
    }

    @Test
    void testNullAndEmptyCollectionSafety() {
        // Simulate missing objects directly on the model
        MxPacs00200112 emptyModel = new MxPacs00200112();
        emptyModel.setFIToFIPmtStsRpt(new FIToFIPaymentStatusReportV12()); // TxInfAndSts is missing

        SemanticRuleEngine engine = new SemanticRuleEngine();
        engine.addRule(new Pacs002RejectReasonRule());

        // Should not throw NPE
        SemanticValidationResult semRes = engine.evaluate(emptyModel, new RuleContext() {});
        assertFalse(semRes.hasFailures()); // Doesn't fail, safely iterates empty list (0 elements)
    }
}
