package com.prowidesoftware.swift.model.mx;

import com.fss.iso20022.api.ValidationResponse;
import com.prowidesoftware.swift.model.mx.validation.ISOParser;
import com.prowidesoftware.swift.model.mx.validation.ISOParserResult;
import com.prowidesoftware.swift.model.mx.validation.builder.ValidationResponseBuilder;
import org.junit.jupiter.api.Test;

public class TestAcmtSynthetic {

    private void runTest(String name, String xml) {
        System.out.println("--- " + name + " ---");
        try {
            ISOParserResult result = ISOParser.parse(xml);
            ValidationResponse response = ValidationResponseBuilder.build(result, null);
            System.out.println("Identification = " + (result.getIdentifier() != null ? "SUCCESS" : "FAIL"));
            System.out.println("XSD = " + result.getSchemaStatus());
            System.out.println("Model = " + result.getModelStatus());
            System.out.println("Final Response = " + response.getStatus());
            if (response.getErrors() != null && !response.getErrors().isEmpty()) {
                System.out.println(
                        "Error Category = " + response.getErrors().get(0).getCategory());
                System.out.println(
                        "Error Message = " + response.getErrors().get(0).getMessage());
            }
        } catch (Throwable t) {
            System.out.println("Exception: " + t.getMessage());
            t.printStackTrace(System.out);
        }
        System.out.println();
    }

    @Test
    public void testAcmt035_Valid() {
        String valid = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:acmt.035.001.02\">\n"
                + "    <AcctSwtchPmtRspn>\n"
                + "        <MsgId>\n"
                + "            <Id>MSG123</Id>\n"
                + "            <CreDtTm>2023-10-06T09:00:00Z</CreDtTm>\n"
                + "        </MsgId>\n"
                + "        <AcctSwtchDtls>\n"
                + "            <UnqRefNb>REF123</UnqRefNb>\n"
                + "            <RtgUnqRefNb>RTG123</RtgUnqRefNb>\n"
                + "            <SwtchTp>FULL</SwtchTp>\n"
                + "        </AcctSwtchDtls>\n"
                + "    </AcctSwtchPmtRspn>\n"
                + "</Document>";
        runTest("Synthetic Valid (acmt.035)", valid);
    }

    @Test
    public void testAcmt035_MissingRequired() {
        String missingReq = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:acmt.035.001.02\">\n"
                + "    <AcctSwtchPmtRspn>\n"
                + "        <AcctSwtchDtls>\n"
                + "            <UnqRefNb>REF123</UnqRefNb>\n"
                + "            <RtgUnqRefNb>RTG123</RtgUnqRefNb>\n"
                + "            <SwtchTp>FULL</SwtchTp>\n"
                + "        </AcctSwtchDtls>\n"
                + "    </AcctSwtchPmtRspn>\n"
                + "</Document>";
        runTest("Negative 1 - Missing Required", missingReq);
    }

    @Test
    public void testAcmt035_InvalidEnum() {
        String invalidEnum = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:acmt.035.001.02\">\n"
                + "    <AcctSwtchPmtRspn>\n"
                + "        <MsgId>\n"
                + "            <Id>MSG123</Id>\n"
                + "            <CreDtTm>2023-10-06T09:00:00Z</CreDtTm>\n"
                + "        </MsgId>\n"
                + "        <AcctSwtchDtls>\n"
                + "            <UnqRefNb>REF123</UnqRefNb>\n"
                + "            <RtgUnqRefNb>RTG123</RtgUnqRefNb>\n"
                + "            <SwtchTp>INVALID_ENUM</SwtchTp>\n"
                + "        </AcctSwtchDtls>\n"
                + "    </AcctSwtchPmtRspn>\n"
                + "</Document>";
        runTest("Negative 2 - Invalid Enum", invalidEnum);
    }

    @Test
    public void testAcmt035_InvalidLength() {
        String invalidLen = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:acmt.035.001.02\">\n"
                + "    <AcctSwtchPmtRspn>\n"
                + "        <MsgId>\n"
                + "            <Id>THIS_ID_IS_WAY_TOO_LONG_AND_EXCEEDS_THE_MAXIMUM_LENGTH_OF_35_CHARACTERS</Id>\n"
                + "            <CreDtTm>2023-10-06T09:00:00Z</CreDtTm>\n"
                + "        </MsgId>\n"
                + "        <AcctSwtchDtls>\n"
                + "            <UnqRefNb>REF123</UnqRefNb>\n"
                + "            <RtgUnqRefNb>RTG123</RtgUnqRefNb>\n"
                + "            <SwtchTp>FULL</SwtchTp>\n"
                + "        </AcctSwtchDtls>\n"
                + "    </AcctSwtchPmtRspn>\n"
                + "</Document>";
        runTest("Negative 3 - Invalid Length", invalidLen);
    }

    @Test
    public void testAcmt035_Malformed() {
        String valid = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:acmt.035.001.02\">\n"
                + "    <AcctSwtchPmtRspn>\n"
                + "        <MsgId>\n"
                + "            <Id>MSG123</Id>\n"
                + "            <CreDtTm>2023-10-06T09:00:00Z</CreDtTm>\n"
                + "        </MsgId>\n"
                + "        <AcctSwtchDtls>\n"
                + "            <UnqRefNb>REF123</UnqRefNb>\n"
                + "            <RtgUnqRefNb>RTG123</RtgUnqRefNb>\n"
                + "            <SwtchTp>FULL</SwtchTp>\n"
                + "        </AcctSwtchDtls>\n"
                + "    </AcctSwtchPmtRspn>\n"
                + "</Document>";
        String malformed = valid.substring(0, valid.length() - 15);
        runTest("Negative 4 - Malformed XML", malformed);
    }

    @Test
    public void testAcmt036_Valid() {
        String validAcmt036 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:acmt.036.001.01\">\n"
                + "    <AcctSwtchTermntnSwtch>\n"
                + "        <MsgId>\n"
                + "            <Id>MSG999</Id>\n"
                + "            <CreDtTm>2023-10-06T09:00:00Z</CreDtTm>\n"
                + "        </MsgId>\n"
                + "        <AcctSwtchDtls>\n"
                + "            <UnqRefNb>REF999</UnqRefNb>\n"
                + "            <RtgUnqRefNb>RTG999</RtgUnqRefNb>\n"
                + "            <SwtchTp>PART</SwtchTp>\n"
                + "        </AcctSwtchDtls>\n"
                + "    </AcctSwtchTermntnSwtch>\n"
                + "</Document>";
        runTest("Second ACMT - Synthetic Valid (acmt.036)", validAcmt036);
    }

    @Test
    public void testAcmt015_MissingModel() {
        String validAcmt015 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:acmt.015.001.05\">\n"
                + "    <AcctExcldMndtMntncReq>\n"
                + "        <Refs>\n"
                + "            <MsgId>\n"
                + "                <Id>ID123</Id>\n"
                + "                <CreDtTm>2023-10-06T09:00:00Z</CreDtTm>\n"
                + "            </MsgId>\n"
                + "            <PrcId>\n"
                + "                <Id>PRC123</Id>\n"
                + "                <CreDtTm>2023-10-06T09:00:00Z</CreDtTm>\n"
                + "            </PrcId>\n"
                + "        </Refs>\n"
                + "        <Acct>\n"
                + "            <Id>\n"
                + "                <Othr>\n"
                + "                    <Id>ACCT123</Id>\n"
                + "                </Othr>\n"
                + "            </Id>\n"
                + "            <Ccy>USD</Ccy>\n"
                + "        </Acct>\n"
                + "        <AcctSvcrId>\n"
                + "            <FinInstnId>\n"
                + "                <BICFI>BBBBUS33XXX</BICFI>\n"
                + "            </FinInstnId>\n"
                + "        </AcctSvcrId>\n"
                + "    </AcctExcldMndtMntncReq>\n"
                + "</Document>";
        runTest("Missing Prowide Model Case (acmt.015)", validAcmt015);
    }
}
