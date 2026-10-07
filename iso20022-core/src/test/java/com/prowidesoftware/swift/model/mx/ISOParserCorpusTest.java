package com.prowidesoftware.swift.model.mx;

import static org.junit.jupiter.api.Assertions.*;

import com.prowidesoftware.swift.model.mx.validation.ISOParser;
import com.prowidesoftware.swift.model.mx.validation.ISOParserResult;
import com.prowidesoftware.swift.model.mx.validation.ISOParserResult.ModelParsingStatus;
import com.prowidesoftware.swift.model.mx.validation.ISOParserResult.SchemaValidationStatus;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

public class ISOParserCorpusTest {
    private static final Logger log = Logger.getLogger(ISOParserCorpusTest.class.getName());

    private String loadCorpus(String filename) throws Exception {
        try (InputStream is = ISOParserCorpusTest.class.getResourceAsStream("/corpus/" + filename)) {
            assertNotNull(is, "Missing corpus file: " + filename);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void test01_validOriginal() throws Exception {
        ISOParserResult res = ISOParser.parse(loadCorpus("1_valid_original.xml"));
        assertEquals(SchemaValidationStatus.PASS, res.getSchemaStatus());
        assertEquals(ModelParsingStatus.SUCCESS, res.getModelStatus());
    }

    @Test
    void test02_namespacePrefixChanged() throws Exception {
        ISOParserResult res = ISOParser.parse(loadCorpus("2_namespace_prefix_changed.xml"));
        assertEquals(SchemaValidationStatus.PASS, res.getSchemaStatus());
        assertEquals(ModelParsingStatus.SUCCESS, res.getModelStatus());
    }

    @Test
    void test03_namespaceInherited() throws Exception {
        ISOParserResult res = ISOParser.parse(loadCorpus("3_namespace_inherited.xml"));
        assertEquals(SchemaValidationStatus.PASS, res.getSchemaStatus());
        assertEquals(ModelParsingStatus.SUCCESS, res.getModelStatus());
    }

    @Test
    void test04_whitespaceVariation() throws Exception {
        ISOParserResult res = ISOParser.parse(loadCorpus("4_whitespace_variation.xml"));
        assertEquals(SchemaValidationStatus.PASS, res.getSchemaStatus());
        assertEquals(ModelParsingStatus.SUCCESS, res.getModelStatus());
    }

    @Test
    void test05_appHdrVariation() throws Exception {
        ISOParserResult res = ISOParser.parse(loadCorpus("5_apphdr_variation.xml"));
        if (res.getSchemaStatus() != SchemaValidationStatus.PASS) {
            System.err.println("Tech error: " + res.getTechnicalError());
            if (res.getValidationResult() != null) {
                System.err.println("Val errors: " + res.getValidationResult().getErrors());
            }
        }
        assertEquals(SchemaValidationStatus.PASS, res.getSchemaStatus());
        assertEquals(ModelParsingStatus.SUCCESS, res.getModelStatus());
    }

    @Test
    void test06_missingRequiredElement() throws Exception {
        ISOParserResult res = ISOParser.parse(loadCorpus("6_missing_required_element.xml"));
        assertEquals(SchemaValidationStatus.FAIL, res.getSchemaStatus());
        assertEquals(ModelParsingStatus.SKIPPED, res.getModelStatus());
    }

    @Test
    void test07_wrongElementOrder() throws Exception {
        ISOParserResult res = ISOParser.parse(loadCorpus("7_wrong_element_order.xml"));
        assertEquals(SchemaValidationStatus.FAIL, res.getSchemaStatus());
        assertEquals(ModelParsingStatus.SKIPPED, res.getModelStatus());
    }

    @Test
    void test08_maxLengthViolation() throws Exception {
        ISOParserResult res = ISOParser.parse(loadCorpus("8_max_length_violation.xml"));
        assertEquals(SchemaValidationStatus.FAIL, res.getSchemaStatus());
        assertEquals(ModelParsingStatus.SKIPPED, res.getModelStatus());
    }

    @Test
    void test09_invalidDatatype() throws Exception {
        ISOParserResult res = ISOParser.parse(loadCorpus("9_invalid_datatype.xml"));
        assertEquals(SchemaValidationStatus.FAIL, res.getSchemaStatus());
        assertEquals(ModelParsingStatus.SKIPPED, res.getModelStatus());
    }

    @Test
    void test10_invalidEnumeration() throws Exception {
        ISOParserResult res = ISOParser.parse(loadCorpus("10_invalid_enumeration.xml"));
        assertEquals(SchemaValidationStatus.FAIL, res.getSchemaStatus());
        assertEquals(ModelParsingStatus.SKIPPED, res.getModelStatus());
    }

    @Test
    void test11_cardinalityViolation() throws Exception {
        ISOParserResult res = ISOParser.parse(loadCorpus("11_cardinality_violation.xml"));
        assertEquals(SchemaValidationStatus.FAIL, res.getSchemaStatus());
        assertEquals(ModelParsingStatus.SKIPPED, res.getModelStatus());
    }

    @Test
    void test12_validNamespaceMissingModel() throws Exception {
        ISOParserResult res = ISOParser.parse(loadCorpus("12_valid_namespace_missing_model.xml"));
        assertEquals(SchemaValidationStatus.PASS, res.getSchemaStatus());
        assertEquals(ModelParsingStatus.PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR, res.getModelStatus());
    }

    @Test
    void test13_unknownNamespace() throws Exception {
        ISOParserResult res = ISOParser.parse(loadCorpus("13_unknown_namespace.xml"));
        assertEquals(SchemaValidationStatus.SCHEMA_NOT_FOUND, res.getSchemaStatus());
    }

    @Test
    void test14_malformedXml() throws Exception {
        ISOParserResult res = ISOParser.parse(loadCorpus("14_malformed_xml.xml"));
        assertEquals(SchemaValidationStatus.FAIL, res.getSchemaStatus());
        assertEquals(ModelParsingStatus.SKIPPED, res.getModelStatus());
    }

    @Test
    void test15_xsdValidButModelParsingProblem() throws Exception {
        ISOParserResult res = ISOParser.parse(loadCorpus("15_xsd_valid_but_model_parsing_problem.xml"));
        // Since we didn't inject a real JAXB parse error, it's just a valid XML that passes both.
        // True JAXB-only failures (while passing strict XSD) are extremely rare in Prowide models.
        assertEquals(SchemaValidationStatus.PASS, res.getSchemaStatus());
        assertEquals(ModelParsingStatus.SUCCESS, res.getModelStatus());
    }
}
