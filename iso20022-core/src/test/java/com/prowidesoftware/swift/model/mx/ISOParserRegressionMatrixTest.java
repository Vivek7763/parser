package com.prowidesoftware.swift.model.mx;

import static org.junit.jupiter.api.Assertions.*;

import com.prowidesoftware.swift.model.mx.validation.ISOParser;
import com.prowidesoftware.swift.model.mx.validation.ISOParserResult;
import com.prowidesoftware.swift.model.mx.validation.ISOParserResult.ModelParsingStatus;
import com.prowidesoftware.swift.model.mx.validation.ISOParserResult.SchemaValidationStatus;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Regression matrix keeping message identification, XSD validation, and JAXB parsing outcomes distinct. */
class ISOParserRegressionMatrixTest {

    @Test
    void validXmlWithSchemaAndGeneratedModelPassesBothStages() {
        ISOParserResult result = ISOParser.parse(validPacs002());

        assertEquals(SchemaValidationStatus.PASS, result.getSchemaStatus());
        assertEquals(ModelParsingStatus.SUCCESS, result.getModelStatus());
        assertNotNull(result.getParsedModel());
        assertNotNull(result.getIdentifier());
        assertEquals("pacs.002.001.12", result.getIdentifier().getFullMessageType());
    }

    @Test
    void validXmlWithSchemaButNoGeneratedModelStillPassesSchema() throws IOException {
        ISOParserResult result = ISOParser.parse(fixture("pacs.008.001.14.xml"));

        assertEquals(SchemaValidationStatus.PASS, result.getSchemaStatus());
        assertEquals(ModelParsingStatus.PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR, result.getModelStatus());
        assertNull(result.getParsedModel());
    }

    @Test
    void xsdInvalidXmlFailsValidationAndSkipsJaxb() throws IOException {
        ISOParserResult result = ISOParser.parse(fixture("pacs.008.001.14_invalid.xml"));

        assertEquals(SchemaValidationStatus.FAIL, result.getSchemaStatus());
        assertEquals(ModelParsingStatus.SKIPPED, result.getModelStatus());
        assertNotNull(result.getValidationResult());
        assertFalse(result.getValidationResult().isValid());
        assertNull(result.getParsedModel());
    }

    @Test
    void missingSchemaIsReportedAndModelParsingIsStillAttempted() {
        // Contract: SCHEMA_NOT_FOUND does not suppress JAXB parsing. This message's generated model is present.
        ISOParserResult result = ISOParser.parse(validCamt053V08());

        assertEquals(SchemaValidationStatus.SCHEMA_NOT_FOUND, result.getSchemaStatus());
        assertEquals(ModelParsingStatus.SUCCESS, result.getModelStatus());
        assertNotNull(result.getParsedModel());
        assertNull(result.getValidationResult());
    }

    @Test
    void malformedXmlIsClassifiedCorrectly() {
        // No namespace → completely unidentifiable
        ISOParserResult result = ISOParser.parse("<Document><FIToFIPmtStsRpt>");

        assertEquals(SchemaValidationStatus.UNIDENTIFIABLE_INPUT, result.getSchemaStatus());
        assertNotEquals(ModelParsingStatus.SUCCESS, result.getModelStatus());
        assertNull(result.getParsedModel());
        assertNull(result.getIdentifier());
    }

    @Test
    void malformedXmlWithNamespaceIsNotSchemaPassing() {
        // Has a namespace (so can be identified) but is truncated — SAX fails during validation
        ISOParserResult result =
                ISOParser.parse("<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\"><FIToFIPmtStsRpt>");

        assertNotEquals(SchemaValidationStatus.PASS, result.getSchemaStatus());
        assertNotEquals(ModelParsingStatus.SUCCESS, result.getModelStatus());
        assertNull(result.getParsedModel());
    }

    @Test
    void unknownOrMalformedIsoNamespaceDoesNotCrashAndDoesNotReportSuccess() {
        ISOParserResult unknown =
                ISOParser.parse("<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:zzzz.999.999.99\"/>");
        ISOParserResult noNs = ISOParser.parse("<Document/>");

        assertNotNull(unknown);
        assertNotNull(noNs);
        assertNotEquals(SchemaValidationStatus.PASS, unknown.getSchemaStatus());
        assertNotEquals(ModelParsingStatus.SUCCESS, unknown.getModelStatus());
        // <Document/> has no namespace → UNIDENTIFIABLE
        assertEquals(SchemaValidationStatus.UNIDENTIFIABLE_INPUT, noNs.getSchemaStatus());
        assertEquals(ModelParsingStatus.PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR, noNs.getModelStatus());
        assertNull(noNs.getParsedModel());
        assertNull(noNs.getIdentifier());
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private static String validPacs002() {
        return "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\">"
                + "<FIToFIPmtStsRpt><GrpHdr><MsgId>MSG-1</MsgId>"
                + "<CreDtTm>2023-10-01T12:00:00Z</CreDtTm></GrpHdr>"
                + "<TxInfAndSts><OrgnlInstrId>INSTR-1</OrgnlInstrId><TxSts>ACCP</TxSts>"
                + "</TxInfAndSts></FIToFIPmtStsRpt></Document>";
    }

    private static String validCamt053V08() {
        return "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:camt.053.001.08\">"
                + "<BkToCstmrStmt><GrpHdr><MsgId>MSG-1</MsgId>"
                + "<CreDtTm>2023-10-01T12:00:00Z</CreDtTm></GrpHdr></BkToCstmrStmt></Document>";
    }

    private static String fixture(String name) throws IOException {
        try (InputStream input = ISOParserRegressionMatrixTest.class.getResourceAsStream("/" + name)) {
            assertNotNull(input, "Missing test fixture: " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
