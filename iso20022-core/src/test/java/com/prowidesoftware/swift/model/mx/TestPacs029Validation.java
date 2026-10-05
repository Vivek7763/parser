package com.prowidesoftware.swift.model.mx;

import java.nio.file.Files;
import java.nio.file.Paths;

public class TestPacs029Validation {
    public static void main(String[] args) throws Exception {
        System.out.println("---- Step 5: Testing an Existing Supported Version (pacs.029.001.02) ----");

        String xml = new String(Files.readAllBytes(Paths.get("iso20022-core/src/test/resources/pacs.029.001.02.xml")));

        System.out.println("1. Identifying message without JAXB model...");
        com.prowidesoftware.swift.model.mx.validation.ISOParserResult result =
                com.prowidesoftware.swift.model.mx.validation.ISOParser.parseAndValidate(xml);

        System.out.println("2. Schema validation...");
        if (result.getSchemaStatus()
                == com.prowidesoftware.swift.model.mx.validation.ISOParserResult.SchemaValidationStatus.PASS) {
            System.out.println("   XSD Validation: PASS");
        } else {
            System.out.println("   XSD Validation: FAIL - " + result.getValidationResult());
            System.exit(1);
        }

        System.out.println("3. Prowide parsing...");
        if (result.getModelStatus()
                == com.prowidesoftware.swift.model.mx.validation.ISOParserResult.ModelParsingStatus.SUCCESS) {
            System.out.println("   JAXB Parsing: PASS ("
                    + result.getParsedModel().getClass().getName() + ")");
        } else {
            System.out.println("   JAXB Parsing: FAIL");
            System.exit(1);
        }

        System.out.println("   End-to-end integration successful.");
    }
}
