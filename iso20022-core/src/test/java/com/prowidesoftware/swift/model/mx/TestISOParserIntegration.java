package com.prowidesoftware.swift.model.mx;

import static org.junit.jupiter.api.Assertions.*;

import com.prowidesoftware.swift.model.mx.validation.ISOParser;
import com.prowidesoftware.swift.model.mx.validation.ISOParserResult;
import com.prowidesoftware.swift.model.mx.validation.ISOParserResult.ModelParsingStatus;
import com.prowidesoftware.swift.model.mx.validation.ISOParserResult.SchemaValidationStatus;
import org.junit.jupiter.api.Test;

/** Legacy integration test retained for regression continuity. Uses the canonical ISOParser.parse() API. */
public class TestISOParserIntegration {

    @Test
    public void testCaseA() {
        ISOParserResult result = ISOParser.parse(pacs002ValidXml());
        assertEquals(SchemaValidationStatus.PASS, result.getSchemaStatus());
        assertEquals(ModelParsingStatus.SUCCESS, result.getModelStatus());
        assertNotNull(result.getParsedModel());
    }

    @Test
    public void testCaseB() {
        // pacs.008.001.14 — schema available, no generated Prowide model
        String validXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.14\">\n"
                + "    <FIToFICstmrCdtTrf>\n"
                + "        <GrpHdr>\n"
                + "            <MsgId>MSGID123</MsgId>\n"
                + "            <CreDtTm>2023-10-01T12:00:00</CreDtTm>\n"
                + "            <NbOfTxs>1</NbOfTxs>\n"
                + "            <SttlmInf><SttlmMtd>INDA</SttlmMtd></SttlmInf>\n"
                + "        </GrpHdr>\n"
                + "        <CdtTrfTxInf>\n"
                + "            <PmtId>\n"
                + "                <InstrId>INSTR123</InstrId>\n"
                + "                <EndToEndId>E2E123</EndToEndId>\n"
                + "                <TxId>TX123</TxId>\n"
                + "            </PmtId>\n"
                + "            <IntrBkSttlmAmt Ccy=\"USD\">100.00</IntrBkSttlmAmt>\n"
                + "            <ChrgBr>DEBT</ChrgBr>\n"
                + "            <Dbtr><Nm>Debtor Name</Nm></Dbtr>\n"
                + "            <DbtrAgt><FinInstnId><BICFI>BOFAUS3N</BICFI></FinInstnId></DbtrAgt>\n"
                + "            <CdtrAgt><FinInstnId><BICFI>BOFAUS3N</BICFI></FinInstnId></CdtrAgt>\n"
                + "            <Cdtr><Nm>Creditor Name</Nm></Cdtr>\n"
                + "        </CdtTrfTxInf>\n"
                + "    </FIToFICstmrCdtTrf>\n"
                + "</Document>";

        ISOParserResult result = ISOParser.parse(validXml);
        assertEquals(SchemaValidationStatus.PASS, result.getSchemaStatus());
        assertEquals(ModelParsingStatus.PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR, result.getModelStatus());
        assertNull(result.getParsedModel());
    }

    @Test
    public void testCaseC() {
        // pacs.002.001.12 with MsgId violating Max35Text
        String invalidXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\">\n"
                + "    <FIToFIPmtStsRpt>\n"
                + "        <GrpHdr>\n"
                + "            <MsgId>MSGID1234567890123456789012345678901234567</MsgId>\n"
                + "            <CreDtTm>2023-10-01T12:00:00Z</CreDtTm>\n"
                + "        </GrpHdr>\n"
                + "        <TxInfAndSts><OrgnlInstrId>INSTR123</OrgnlInstrId><TxSts>ACCP</TxSts></TxInfAndSts>\n"
                + "    </FIToFIPmtStsRpt>\n"
                + "</Document>";

        ISOParserResult result = ISOParser.parse(invalidXml);
        assertEquals(SchemaValidationStatus.FAIL, result.getSchemaStatus());
        assertEquals(ModelParsingStatus.SKIPPED, result.getModelStatus());
    }

    @Test
    public void testCaseD() {
        // camt.053.001.08 — no registered XSD, Prowide model exists
        ISOParserResult result = ISOParser.parse(camt053Xml());
        assertEquals(SchemaValidationStatus.SCHEMA_NOT_FOUND, result.getSchemaStatus());
    }

    @Test
    public void testCaseE() {
        // seev.031.001.09 — no registered XSD
        String seevXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:seev.031.001.09\">\n"
                + "    <CorpActnNtfctn><NtfctnGnlInf>"
                + "<NtfctnId>NOTIF123</NtfctnId><NtfctnTp>NEWM</NtfctnTp>"
                + "</NtfctnGnlInf></CorpActnNtfctn>\n"
                + "</Document>";

        ISOParserResult result = ISOParser.parse(seevXml);
        assertEquals(SchemaValidationStatus.SCHEMA_NOT_FOUND, result.getSchemaStatus());
    }

    @Test
    public void testCaseF() {
        // Malformed XML — namespace visible but document broken
        String malformedXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\">\n"
                + "    <FIToFIPmtStsRpt>\n"
                + "        <GrpHdr>\n"
                + "            <MsgId>MSGID123</MsgId>\n"
                + "        </GrpHdr>\n"
                + "</Document";

        ISOParserResult result = ISOParser.parse(malformedXml);
        assertNotEquals(SchemaValidationStatus.PASS, result.getSchemaStatus());
        assertNotEquals(ModelParsingStatus.SUCCESS, result.getModelStatus());
    }

    @Test
    public void testCaseG() {
        // Unknown namespace — not an ISO 20022 namespace at all
        String invalidNsXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Document xmlns=\"urn:unknown:namespace:xyz.123.45\">\n"
                + "    <Data>Test</Data>\n"
                + "</Document>";

        ISOParserResult result = ISOParser.parse(invalidNsXml);
        // Prowide cannot identify this as ISO 20022 → UNIDENTIFIABLE or SCHEMA_NOT_FOUND
        assertNotEquals(SchemaValidationStatus.PASS, result.getSchemaStatus());
        assertNotEquals(ModelParsingStatus.SUCCESS, result.getModelStatus());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private static String pacs002ValidXml() {
        return "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\">"
                + "<FIToFIPmtStsRpt><GrpHdr><MsgId>MSGID123</MsgId>"
                + "<CreDtTm>2023-10-01T12:00:00Z</CreDtTm></GrpHdr>"
                + "<TxInfAndSts><OrgnlInstrId>INSTR123</OrgnlInstrId>"
                + "<TxSts>ACCP</TxSts></TxInfAndSts></FIToFIPmtStsRpt></Document>";
    }

    private static String camt053Xml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.053.001.08\">\n"
                + "    <BkToCstmrStmt><GrpHdr><MsgId>MSGID123</MsgId>"
                + "<CreDtTm>2023-10-01T12:00:00Z</CreDtTm></GrpHdr></BkToCstmrStmt>\n"
                + "</Document>";
    }
}
