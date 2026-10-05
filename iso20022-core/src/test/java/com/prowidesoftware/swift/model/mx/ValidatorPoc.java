package com.prowidesoftware.swift.model.mx;

import java.io.InputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;

public class ValidatorPoc {
    public static void main(String[] args) throws Exception {
        System.out.println("---- Validating Valid XML ----");
        validate("pacs.008.001.07.xml");

        System.out.println("\n---- Validating Invalid XML ----");
        validate("pacs.008.001.07_invalid.xml");
    }

    private static void validate(String filename) throws Exception {
        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = sf.newSchema(ValidatorPoc.class.getResource("/pacs.008.001.07.xsd"));

        try (InputStream input = ValidatorPoc.class.getResourceAsStream("/" + filename)) {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            XMLReader reader = spf.newSAXParser().getXMLReader();

            org.xml.sax.XMLFilter filter = new org.xml.sax.helpers.XMLFilterImpl(reader) {
                boolean inDocument = false;

                @Override
                public void startElement(String uri, String localName, String qName, org.xml.sax.Attributes atts)
                        throws org.xml.sax.SAXException {
                    if ("Document".equals(localName)) {
                        inDocument = true;
                    }
                    if (inDocument) {
                        super.startElement(uri, localName, qName, atts);
                    }
                }

                @Override
                public void endElement(String uri, String localName, String qName) throws org.xml.sax.SAXException {
                    if (inDocument) {
                        super.endElement(uri, localName, qName);
                    }
                    if ("Document".equals(localName)) {
                        inDocument = false;
                    }
                }

                @Override
                public void characters(char[] ch, int start, int length) throws org.xml.sax.SAXException {
                    if (inDocument) {
                        super.characters(ch, start, length);
                    }
                }
            };

            InputSource is = new InputSource(input);
            SAXSource source = new SAXSource(filter, is);

            Validator validator = schema.newValidator();
            validator.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException exception) throws SAXException {
                    System.out.println("WARNING: " + exception.getMessage());
                }

                @Override
                public void error(SAXParseException exception) throws SAXException {
                    System.out.println("ERROR: " + exception.getMessage());
                }

                @Override
                public void fatalError(SAXParseException exception) throws SAXException {
                    System.out.println("FATAL: " + exception.getMessage());
                }
            });

            try {
                validator.validate(source);
                System.out.println("Validation completed.");
            } catch (Exception e) {
                System.out.println("Validation Exception: " + e.getMessage());
            }
        }
    }
}
