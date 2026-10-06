package com.prowidesoftware.swift.model.mx.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prowidesoftware.swift.model.mx.validation.ISOParserResult.ModelParsingStatus;
import com.prowidesoftware.swift.model.mx.validation.ISOParserResult.SchemaValidationStatus;
import com.prowidesoftware.swift.model.mx.validation.SchemaRegistry.SchemaNotFoundException;
import javax.xml.validation.Schema;
import org.junit.jupiter.api.Test;

class SchemaRegistryClasspathTest {

    @Test
    void testResolveFromClasspath_Success() {
        // This schema exists ONLY in src/test/resources/schemas/pacs/pacs.888.888.88/pacs.888.888.88.xsd
        // It does not exist in the root schemas directory.
        Schema schema = SchemaRegistry.resolve("pacs.888.888.88");
        assertThat(schema).isNotNull();
    }

    @Test
    void testResolveMissingSchema_ThrowsException() {
        assertThatThrownBy(() -> SchemaRegistry.resolve("pacs.888.888.00"))
                .isInstanceOf(SchemaNotFoundException.class)
                .hasMessageContaining("pacs.888.888.00");
    }

    @Test
    void testParserIntegrationWithClasspathSchema() {
        // We will run the parser with an XML that uses the classpath-only schema
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.888.888.88\">\n"
                + "    <TestMessage>\n"
                + "        <Id>12345</Id>\n"
                + "    </TestMessage>\n"
                + "</Document>";

        // Since we are mocking a schema without a corresponding Prowide generated model,
        // we expect the schema to PASS but the model parsing to yield PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR.
        ISOParserResult result = ISOParser.parse(xml);

        assertThat(result.getSchemaStatus()).isEqualTo(SchemaValidationStatus.PASS);
        assertThat(result.isSchemaValid()).isTrue();
        assertThat(result.getValidationErrors()).isEmpty();

        assertThat(result.getModelStatus()).isEqualTo(ModelParsingStatus.PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR);
    }
}
