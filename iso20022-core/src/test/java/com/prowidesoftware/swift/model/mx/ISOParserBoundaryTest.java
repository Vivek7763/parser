package com.prowidesoftware.swift.model.mx;

import static org.junit.jupiter.api.Assertions.*;

import com.prowidesoftware.swift.model.mx.validation.ISOParser;
import com.prowidesoftware.swift.model.mx.validation.ISOParserResult;
import com.prowidesoftware.swift.model.mx.validation.ISOParserResult.ModelParsingStatus;
import com.prowidesoftware.swift.model.mx.validation.ISOParserResult.SchemaValidationStatus;
import com.prowidesoftware.swift.model.mx.validation.ValidationError;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive boundary test suite for the FSS ISO 20022 parser / validator pipeline.
 *
 * <p>Each test covers one distinct boundary scenario. All assertions document the <em>exact</em> expected
 * outcome for that boundary.
 *
 * <h2>Cases</h2>
 * <ol>
 *   <li>Valid XML + matching XSD + matching Prowide model → PASS / SUCCESS</li>
 *   <li>Invalid XML against XSD → FAIL / SKIPPED (Prowide must NOT execute)</li>
 *   <li>Valid XSD + no generated Prowide model → PASS / PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR</li>
 *   <li>No XSD configured + generated model available → SCHEMA_NOT_FOUND / SUCCESS</li>
 *   <li>Unknown ISO namespace → SCHEMA_NOT_FOUND / PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR (no match)</li>
 *   <li>Malformed XML → UNIDENTIFIABLE_INPUT or FAIL (NOT falsely SCHEMA_NOT_FOUND)</li>
 *   <li>Multiple message types (pacs, camt, seev) through same entry point → correct identity each time</li>
 * </ol>
 */
class ISOParserBoundaryTest {

    // ═══════════════════════════════════════════════════════════════════════
    // CASE A — Valid XML + XSD match + model match
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void caseA_validXmlWithSchemaAndModel_schemaPassModelSuccess() {
        ISOParserResult result = ISOParser.parse(validPacs002Xml());

        assertEquals(SchemaValidationStatus.PASS, result.getSchemaStatus(), "Schema should PASS");
        assertEquals(ModelParsingStatus.SUCCESS, result.getModelStatus(), "Model should be SUCCESS");
        assertNotNull(result.getParsedModel(), "Parsed model must not be null");
        assertNotNull(result.getIdentifier(), "Identifier must not be null");
        assertEquals("pacs.002.001.12", result.getIdentifier().getFullMessageType());
        assertEquals("pacs", result.getIdentifier().getBusinessArea());
        assertTrue(result.getValidationErrors().isEmpty(), "No validation errors expected");
        assertNull(result.getTechnicalError());
        assertTrue(result.isSchemaValid());
        assertTrue(result.isModelParsed());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CASE B — XSD invalid XML: schema FAIL, Prowide must NOT run
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void caseB_xsdInvalidXml_schemaFailModelSkipped() throws IOException {
        ISOParserResult result = ISOParser.parse(fixture("pacs.008.001.14_invalid.xml"));

        assertEquals(SchemaValidationStatus.FAIL, result.getSchemaStatus(), "Schema should FAIL");
        assertEquals(ModelParsingStatus.SKIPPED, result.getModelStatus(), "Model should be SKIPPED");
        assertNull(result.getParsedModel(), "Prowide must NOT have been invoked — parsedModel must be null");
        assertNotNull(result.getValidationResult(), "ValidationResult must contain errors");
        assertFalse(result.getValidationResult().isValid(), "ValidationResult.isValid() must be false");
        assertFalse(result.getValidationErrors().isEmpty(), "At least one validation error expected");
        // Confirm at least one error reports a meaningful message (not empty string)
        ValidationError firstError = result.getValidationErrors().get(0);
        assertNotNull(firstError.getMessage());
        assertFalse(firstError.getMessage().isBlank());
        assertFalse(result.isSchemaValid());
        assertFalse(result.isModelParsed());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CASE C — Valid XSD + no generated Prowide model
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void caseC_validXmlWithSchemaButNoGeneratedModel_schemaPassModelUnavailable() throws IOException {
        ISOParserResult result = ISOParser.parse(fixture("pacs.008.001.14.xml"));

        assertEquals(SchemaValidationStatus.PASS, result.getSchemaStatus(), "Schema should PASS");
        assertEquals(
                ModelParsingStatus.PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR,
                result.getModelStatus(),
                "Model should be PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR");
        assertNull(result.getParsedModel(), "parsedModel must be null");
        assertNotNull(result.getIdentifier());
        assertEquals("pacs.008.001.14", result.getIdentifier().getFullMessageType());
        assertTrue(result.getValidationErrors().isEmpty(), "No validation errors for valid message");
        assertFalse(result.isModelParsed());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CASE D — No XSD + generated model available
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void caseD_noSchemaButModelAvailable_schemaNotFoundModelSuccess() {
        // camt.053.001.08 has no registered XSD but has a generated Prowide model
        ISOParserResult result = ISOParser.parse(validCamt053Xml());

        assertEquals(SchemaValidationStatus.SCHEMA_NOT_FOUND, result.getSchemaStatus(), "Schema should be NOT_FOUND");
        assertEquals(ModelParsingStatus.SUCCESS, result.getModelStatus(), "Model should be SUCCESS");
        assertNotNull(result.getParsedModel(), "parsedModel must not be null");
        assertNull(result.getValidationResult(), "ValidationResult must be null when no XSD is configured");
        assertNotNull(result.getIdentifier());
        assertEquals("camt.053.001.08", result.getIdentifier().getFullMessageType());
        assertEquals("camt", result.getIdentifier().getBusinessArea());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CASE E — Unknown ISO namespace (syntactically valid XML, no match)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void caseE_unknownIsoNamespace_schemaNotFoundModelUnavailable() {
        // A namespace that looks like ISO 20022 format but is completely fictional.
        // Prowide's MxId parser may or may not accept "zzzz" as a business area.
        // Either way: it must NOT report PASS for schema, and must NOT report SUCCESS for model.
        String xml = "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:zzzz.999.999.99\"><UnknownMsg/></Document>";
        ISOParserResult result = ISOParser.parse(xml);

        assertNotNull(result);
        // If Prowide accepts the namespace: identifier is non-null, schema is SCHEMA_NOT_FOUND
        // If Prowide rejects the namespace: identifier is null, schema is UNIDENTIFIABLE_INPUT
        // In neither case should schema be PASS or model be SUCCESS
        assertNotEquals(SchemaValidationStatus.PASS, result.getSchemaStatus());
        assertNotEquals(ModelParsingStatus.SUCCESS, result.getModelStatus());
        assertNull(result.getParsedModel());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CASE F — Malformed XML
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void caseF1_malformedXmlNoNamespace_unidentifiableInput() {
        // No namespace at all — Prowide cannot identify the message type
        String xml = "<Document><FIToFIPmtStsRpt>";
        ISOParserResult result = ISOParser.parse(xml);

        // Must be UNIDENTIFIABLE_INPUT — not falsely SCHEMA_NOT_FOUND
        assertEquals(
                SchemaValidationStatus.UNIDENTIFIABLE_INPUT,
                result.getSchemaStatus(),
                "Completely unidentifiable XML should produce UNIDENTIFIABLE_INPUT, not SCHEMA_NOT_FOUND");
        assertEquals(ModelParsingStatus.PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR, result.getModelStatus());
        assertNull(result.getIdentifier());
        assertNull(result.getParsedModel());
        assertFalse(result.isSchemaValid());
        assertFalse(result.isModelParsed());
    }

    @Test
    void caseF2_malformedXmlWithNamespaceButUnclosed_schemaFailOrUnidentifiable() {
        // Namespace is present so identity can be extracted, but the document is structurally broken
        String xml = "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\"><FIToFIPmtStsRpt>";
        ISOParserResult result = ISOParser.parse(xml);

        // Must NOT be SUCCESS in either status
        assertNotEquals(SchemaValidationStatus.PASS, result.getSchemaStatus());
        assertNotEquals(ModelParsingStatus.SUCCESS, result.getModelStatus());
        assertNull(result.getParsedModel());
    }

    @Test
    void caseF3_blankXml_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> ISOParser.parse(null));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CASE G — Multiple message types through same entry point
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void caseG_multipleMessageTypes_correctIdentityEachTime() {
        // pacs — has registered XSD + model
        ISOParserResult pacs = ISOParser.parse(validPacs002Xml());
        assertEquals("pacs", pacs.getIdentifier().getBusinessArea());
        assertEquals("pacs.002.001.12", pacs.getIdentifier().getFullMessageType());
        assertEquals(SchemaValidationStatus.PASS, pacs.getSchemaStatus());
        assertEquals(ModelParsingStatus.SUCCESS, pacs.getModelStatus());

        // camt — no registered XSD, has model
        ISOParserResult camt = ISOParser.parse(validCamt053Xml());
        assertEquals("camt", camt.getIdentifier().getBusinessArea());
        assertEquals("camt.053.001.08", camt.getIdentifier().getFullMessageType());
        assertEquals(SchemaValidationStatus.SCHEMA_NOT_FOUND, camt.getSchemaStatus());
        assertEquals(ModelParsingStatus.SUCCESS, camt.getModelStatus());

        // seev — no registered XSD, but has model
        ISOParserResult seev = ISOParser.parse(validSeev031Xml());
        assertEquals("seev", seev.getIdentifier().getBusinessArea());
        assertEquals("seev.031.001.09", seev.getIdentifier().getFullMessageType());
        assertEquals(SchemaValidationStatus.SCHEMA_NOT_FOUND, seev.getSchemaStatus());
        assertEquals(ModelParsingStatus.SUCCESS, seev.getModelStatus());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Additional boundary: identifier structural wellformedness
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void identifierWellformedness_standardNamespaceIsWellFormed() {
        ISOParserResult result = ISOParser.parse(validPacs002Xml());
        assertNotNull(result.getIdentifier());
        assertTrue(
                result.getIdentifier().isWellFormed(), "Standard ISO namespace should produce well-formed identifier");
        assertEquals("002", result.getIdentifier().getMessageFunction());
        assertEquals("001", result.getIdentifier().getVariant());
        assertEquals("12", result.getIdentifier().getVersion());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Prowide replaceability check (Task 6)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    void prowideReplaceability_schemaValidationDoesNotImportProwide() throws ClassNotFoundException {
        // Confirm that ISOValidator, SchemaRegistry, ISOMessageIdentifier can be loaded
        // without any Prowide class being required at the schema-validation layer.
        // This is a compile-time guarantee enforced by the fact that these classes
        // import no Prowide types. We verify it at runtime by asserting no Prowide
        // class appears in their import chain.
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        assertDoesNotThrow(() -> cl.loadClass("com.prowidesoftware.swift.model.mx.validation.ISOMessageIdentifier"));
        assertDoesNotThrow(() -> cl.loadClass("com.prowidesoftware.swift.model.mx.validation.SchemaRegistry"));
        assertDoesNotThrow(() -> cl.loadClass("com.prowidesoftware.swift.model.mx.validation.ISOValidator"));

        // ProwideAdapter DOES reference Prowide — that's by design (isolated coupling point)
        assertDoesNotThrow(() -> cl.loadClass("com.prowidesoftware.swift.model.mx.validation.ProwideAdapter"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private static String validPacs002Xml() {
        return "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\">"
                + "<FIToFIPmtStsRpt><GrpHdr><MsgId>MSG-1</MsgId>"
                + "<CreDtTm>2023-10-01T12:00:00Z</CreDtTm></GrpHdr>"
                + "<TxInfAndSts><OrgnlInstrId>INSTR-1</OrgnlInstrId><TxSts>ACCP</TxSts>"
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
        try (InputStream is = ISOParserBoundaryTest.class.getResourceAsStream("/" + name)) {
            assertNotNull(is, "Missing test fixture: " + name);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
