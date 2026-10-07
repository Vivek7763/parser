package com.prowidesoftware.swift.model.mx;

import static org.junit.jupiter.api.Assertions.*;

import com.prowidesoftware.swift.model.mx.validation.*;
import com.prowidesoftware.swift.model.mx.validation.semantic.*;
import org.junit.jupiter.api.Test;

public class ProwideArchitectureWalkthroughTest {

    @Test
    public void testEndToEndPipeline() throws Exception {
        // 1. We construct a payload wrapping AppHdr + Document
        String payload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<RequestPayload>\n"
                + "  <AppHdr xmlns=\"urn:iso:std:iso:20022:tech:xsd:head.001.001.01\">\n"
                + "    <MsgDefIdr>pacs.002.001.12</MsgDefIdr>\n"
                + "  </AppHdr>\n"
                + "  <Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\">\n"
                + "    <FIToFIPmtStsRpt>\n"
                + "      <GrpHdr>\n"
                + "        <MsgId>MSGID123</MsgId>\n"
                + "        <CreDtTm>2023-10-01T12:00:00Z</CreDtTm>\n"
                + "      </GrpHdr>\n"
                + "    </FIToFIPmtStsRpt>\n"
                + "  </Document>\n"
                + "</RequestPayload>";

        // 2. Set up semantic engine with SpEL Helpers and Profile
        SemanticRuleEngine engine = new SemanticRuleEngine(new SpELRuleEvaluator());

        SemanticRuleDefinition rule = new SemanticRuleDefinition();
        rule.setRuleId("CHK-01");
        rule.setMessageType("pacs.002");
        rule.setSeverity("ERROR");
        rule.setErrorPath("GrpHdr.MsgId");
        rule.setExpression("#parseInt('10') == 10 && #getCurrencyDecimals('USD') == 2"); // Test SpEL helpers
        rule.setMessage("Test rule with SpEL helpers");
        rule.setProfiles(java.util.Collections.singletonList("differentProfile"));
        engine.addRule(rule);

        // 3. Execute parser
        RuleContext context = new RuleContext("strict");
        ISOParserResult result = ISOParser.parse(payload);

        // 4. Assert structural validation pass
        assertEquals(ISOParserResult.SchemaValidationStatus.PASS, result.getSchemaStatus(), "Schema must pass");

        SemanticValidationResult semResult1 = engine.validate(result.getParsedModel(), context);

        // 5. Assert semantic validation skips if profile mismatch (the rule doesn't have the 'strict' profile)
        assertEquals(
                0, semResult1.getEvaluatedRuleCount(), "Semantic should be skipped since rule has no matching profile");

        // Add rule with matching profile
        rule.setProfiles(java.util.Collections.singletonList("strict"));
        SemanticValidationResult semResult2 = engine.validate(result.getParsedModel(), context);
        if (!semResult2.getTechnicalErrors().isEmpty()) {
            System.err.println("Tech errors: " + semResult2.getTechnicalErrors());
        }
        assertEquals(1, semResult2.getEvaluatedRuleCount(), "Semantic should be evaluated with strict profile");
        assertFalse(semResult2.hasFailures(), "Semantic should PASS with strict profile");

        // Test AppHdr Mismatch
        String badPayload = payload.replace("pacs.002.001.12</MsgDefIdr>", "pacs.008.001.14</MsgDefIdr>");
        ISOParserResult result3 = ISOParser.parse(badPayload);
        assertEquals(
                ISOParserResult.SchemaValidationStatus.FAIL, result3.getSchemaStatus(), "Schema must fail on mismatch");
        assertTrue(result3.getTechnicalError().contains("mismatch"), "Must report mismatch");
    }
}
