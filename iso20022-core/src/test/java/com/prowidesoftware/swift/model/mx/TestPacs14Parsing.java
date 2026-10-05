package com.prowidesoftware.swift.model.mx;

import java.nio.file.Files;
import java.nio.file.Paths;

public class TestPacs14Parsing {
    public static void main(String[] args) throws Exception {
        String xml = new String(Files.readAllBytes(Paths.get("iso20022-core/src/test/resources/pacs.008.001.14.xml")));
        AbstractMX mx = AbstractMX.parse(xml);
        if (mx == null) {
            System.out.println("Parsed MX is null");
        } else {
            System.out.println("Parsed MX successfully: " + mx.getClass().getName());
        }
    }
}
