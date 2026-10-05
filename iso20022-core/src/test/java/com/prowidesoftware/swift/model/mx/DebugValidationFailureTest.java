package com.prowidesoftware.swift.model.mx;

import com.prowidesoftware.swift.model.mx.validation.ISOParser;
import com.prowidesoftware.swift.model.mx.validation.ISOParserResult;
import com.prowidesoftware.swift.model.mx.validation.ValidationError;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class DebugValidationFailureTest {

    @Test
    public void debugPacs14Validation() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/pacs.008.001.14.xml")) {
            String xml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            ISOParserResult result = ISOParser.parseAndValidate(xml);
            System.out.println("Schema status: " + result.getSchemaStatus());
            System.out.println("Model status: " + result.getModelStatus());
            if (result.getValidationResult() != null) {
                System.out.println("Valid: " + result.getValidationResult().isValid());
                for (ValidationError e : result.getValidationResult().getErrors()) {
                    System.out.println("  " + e);
                }
            } else {
                System.out.println("ValidationResult is null");
            }
        }
    }
}
