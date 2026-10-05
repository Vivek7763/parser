/*
 * Copyright 2006-2023 Prowide
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.prowidesoftware.swift.model.mx;

import com.prowidesoftware.swift.model.MxId;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/** Small runnable example that parses the existing pacs.008 fixture and prints its detected type. */
public final class OneMessageParser {

    private static final String[] SAMPLES = {"pacs.008.001.07.xml", "camt.053.001.07.xml", "seev.031.002.09.xml"};

    private OneMessageParser() {}

    public static void main(String[] args) throws IOException {
        for (String sample : SAMPLES) {
            System.out.println("---- Parsing " + sample + " ----");
            try (InputStream input = OneMessageParser.class.getResourceAsStream("/" + sample)) {
                if (input == null) {
                    throw new IllegalStateException("Classpath resource not found: " + sample);
                }
                String xml;
                try (Scanner scanner = new Scanner(input, StandardCharsets.UTF_8)) {
                    xml = scanner.useDelimiter("\\A").next();
                }

                AbstractMX message = AbstractMX.parse(xml);
                if (message == null) {
                    throw new IllegalStateException("Could not parse " + sample);
                }

                MxId id = message.getMxId();
                System.out.println("Parse successful");
                System.out.println("Input: " + sample);
                System.out.println("Message type: " + id);
                System.out.println("Version: " + id.getVersion());
                System.out.println("Java class: " + message.getClass().getName());
            }
        }
    }
}
