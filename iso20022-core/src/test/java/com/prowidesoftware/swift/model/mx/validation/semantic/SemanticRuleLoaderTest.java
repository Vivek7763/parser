package com.prowidesoftware.swift.model.mx.validation.semantic;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

public class SemanticRuleLoaderTest {

    @Test
    void testLoadValidRules() {
        String json = "[\n" + "  {\n"
                + "    \"ruleId\": \"CHK-001\",\n"
                + "    \"messageType\": \"pacs.002\",\n"
                + "    \"severity\": \"FATAL\",\n"
                + "    \"errorPath\": \"GrpHdr/MsgId\",\n"
                + "    \"expression\": \"FIToFIPmtStsRpt?.grpHdr?.msgId == 'MSG123'\",\n"
                + "    \"message\": \"Invalid MsgId\"\n"
                + "  }\n"
                + "]";

        InputStream is = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        List<SemanticRuleDefinition> rules = SemanticRuleLoader.loadJsonRules(is);

        assertEquals(1, rules.size());
        assertEquals("CHK-001", rules.get(0).getRuleId());
        assertEquals("pacs.002", rules.get(0).getMessageType());
        assertEquals("FATAL", rules.get(0).getSeverity());
        assertEquals("GrpHdr/MsgId", rules.get(0).getErrorPath());
        assertEquals("FIToFIPmtStsRpt?.grpHdr?.msgId == 'MSG123'", rules.get(0).getExpression());
        assertEquals("Invalid MsgId", rules.get(0).getMessage());
    }

    @Test
    void testFailFastOnMissingRuleId() {
        String json =
                "[{\"messageType\": \"pacs.002\", \"expression\": \"true\", \"severity\": \"FATAL\", \"errorPath\": \"A\"}]";
        InputStream is = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> SemanticRuleLoader.loadJsonRules(is));
        assertTrue(e.getMessage().contains("ruleId is required"));
    }

    @Test
    void testFailFastOnMissingExpression() {
        String json =
                "[{\"ruleId\": \"R1\", \"messageType\": \"pacs.002\", \"severity\": \"FATAL\", \"errorPath\": \"A\"}]";
        InputStream is = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));

        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> SemanticRuleLoader.loadJsonRules(is));
        assertTrue(e.getMessage().contains("expression is required"));
    }
}
