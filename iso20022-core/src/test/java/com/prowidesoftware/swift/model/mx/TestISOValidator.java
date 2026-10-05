package com.prowidesoftware.swift.model.mx;

import com.prowidesoftware.swift.model.mx.validation.ISOParser;
import com.prowidesoftware.swift.model.mx.validation.ISOParserResult;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TestISOValidator {
    public static void main(String[] args) throws Exception {
        System.out.println("---- Validating Valid XML (pacs.008.001.14) ----");
        String validXml =
                new String(Files.readAllBytes(Paths.get("iso20022-core/src/test/resources/pacs.008.001.14.xml")));
        ISOParserResult validResult = ISOParser.parseAndValidate(validXml);
        System.out.println(validResult.getValidationResult());

        System.out.println("\n---- Validating Invalid XML (pacs.008.001.14_invalid) ----");
        String invalidXml = new String(
                Files.readAllBytes(Paths.get("iso20022-core/src/test/resources/pacs.008.001.14_invalid.xml")));
        ISOParserResult invalidResult = ISOParser.parseAndValidate(invalidXml);
        System.out.println(invalidResult.getValidationResult());

        System.out.println("\n---- Testing Separation ----");
        System.out.println("Attempting to parse invalid XML with JAXB (Prowide)...");
        try {
            AbstractMX mx = AbstractMX.parse(invalidXml);
            if (mx != null) {
                System.out.println("Prowide successfully parsed the structurally invalid XML into: "
                        + mx.getClass().getName());
            } else {
                System.out.println("Prowide parser returned null");
            }
        } catch (Exception e) {
            System.out.println("Prowide parser threw exception: " + e.getMessage());
        }
    }
}
