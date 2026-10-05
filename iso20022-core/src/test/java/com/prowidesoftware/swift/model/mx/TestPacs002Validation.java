package com.prowidesoftware.swift.model.mx;

import static org.junit.jupiter.api.Assertions.*;

public class TestPacs002Validation {

    public static void main(String[] args) throws Exception {
        String validXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.002.001.12\">\n"
                + "    <FIToFIPmtStsRpt>\n"
                + "        <GrpHdr>\n"
                + "            <MsgId>MSGID123</MsgId>\n"
                + "            <CreDtTm>2023-10-01T12:00:00Z</CreDtTm>\n"
                + "        </GrpHdr>\n"
                + "        <TxInfAndSts>\n"
                + "            <OrgnlInstrId>INSTR123</OrgnlInstrId>\n"
                + "            <TxSts>ACCP</TxSts>\n"
                + "        </TxInfAndSts>\n"
                + "    </FIToFIPmtStsRpt>\n"
                + "</Document>";

        com.prowidesoftware.swift.model.mx.validation.ISOParserResult result =
                com.prowidesoftware.swift.model.mx.validation.ISOParser.parseAndValidate(validXml);

        System.out.println("Validation Valid Result: " + result.getValidationResult());
        if (result.getValidationResult() != null
                && !result.getValidationResult().isValid()) {
            result.getValidationResult()
                    .getErrors()
                    .forEach(e -> System.out.println(
                            "  ERROR at [" + e.getLine() + ":" + e.getColumn() + "]: " + e.getMessage()));
        }
    }
}
