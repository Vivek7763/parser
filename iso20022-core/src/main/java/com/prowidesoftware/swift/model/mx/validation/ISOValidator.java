package com.prowidesoftware.swift.model.mx.validation;

import com.prowidesoftware.swift.utils.SafeXmlUtils;
import java.io.StringReader;
import javax.xml.transform.sax.SAXSource;
import javax.xml.validation.Schema;
import javax.xml.validation.Validator;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLFilter;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;

/**
 * Validates an ISO 20022 XML payload against its authoritative XSD schema.
 *
 * <h2>Document isolation</h2>
 * ISO 20022 messages may be wrapped in outer envelopes (e.g., a {@code <message>} root containing both
 * {@code <AppHdr>} and {@code <Document>}).  This validator replicates Prowide's parsing boundary: only the
 * {@code <Document>} subtree is validated, and namespace bindings from the wrapping element are correctly
 * forwarded into the filter stream via {@code startPrefixMapping}/{@code endPrefixMapping}.
 *
 * <h2>Error accumulation</h2>
 * Validation errors are accumulated into a {@link ValidationResult} without aborting on the first error,
 * giving callers a complete picture of all constraint violations in a single pass.
 */
public final class ISOValidator {

    private ISOValidator() {}

    /**
     * Validates the XML payload against the XSD registered for {@code identifier}.
     *
     * @param xml        the raw XML string (may include AppHdr wrapper)
     * @param identifier the message type used to look up the schema
     * @return a {@link ValidationResult} populated with any constraint violations found; empty errors == valid
     * @throws SchemaRegistry.SchemaNotFoundException    when no XSD is registered for this message type
     * @throws SchemaRegistry.SchemaCompilationException when the XSD exists but cannot be compiled
     * @throws Exception                                 for unexpected SAX/parser infrastructure failures
     */
    public static ValidationResult validate(String xml, ISOMessageIdentifier identifier) throws Exception {
        return validate(xml, identifier, null);
    }

    public static ValidationResult validate(
            String xml, ISOMessageIdentifier docIdentifier, ISOMessageIdentifier appHdrIdentifier) throws Exception {
        if (docIdentifier == null) {
            throw new IllegalArgumentException("docIdentifier must not be null");
        }

        ValidationResult result = new ValidationResult(docIdentifier.getFullMessageType());

        // Validate AppHdr if present
        if (appHdrIdentifier != null) {
            try {
                Schema appHdrSchema = SchemaRegistry.resolve(appHdrIdentifier);
                validateSubtree(xml, appHdrSchema, "AppHdr", result, "[AppHdr] ");
            } catch (SchemaRegistry.SchemaNotFoundException e) {
                // If AppHdr schema is missing, it's missing. Add a warning or let it be.
                result.addError(new ValidationError(
                        "WARNING", "AppHdr schema not found: " + appHdrIdentifier.getFullMessageType(), -1, -1));
            }
        }

        // Validate Document
        Schema docSchema = SchemaRegistry.resolve(docIdentifier);
        validateSubtree(xml, docSchema, "Document", result, "");

        return result;
    }

    private static void validateSubtree(
            String xml, Schema schema, String targetElement, ValidationResult result, String errorPrefix)
            throws Exception {
        XMLReader baseReader = SafeXmlUtils.reader(true, null);

        XMLFilter filter = new XMLFilterImpl(baseReader) {
            private boolean inTarget = false;
            private int depth = 0;

            @Override
            public void startPrefixMapping(String prefix, String uri) throws SAXException {
                super.startPrefixMapping(prefix, uri);
            }

            @Override
            public void endPrefixMapping(String prefix) throws SAXException {
                super.endPrefixMapping(prefix);
            }

            @Override
            public void startElement(String uri, String localName, String qName, org.xml.sax.Attributes atts)
                    throws SAXException {
                if (!inTarget && targetElement.equals(localName)) {
                    inTarget = true;
                }
                if (inTarget) {
                    depth++;
                    super.startElement(uri, localName, qName, atts);
                }
            }

            @Override
            public void endElement(String uri, String localName, String qName) throws SAXException {
                if (inTarget) {
                    super.endElement(uri, localName, qName);
                    depth--;
                    if (depth == 0) {
                        inTarget = false;
                    }
                }
            }

            @Override
            public void characters(char[] ch, int start, int length) throws SAXException {
                if (inTarget) {
                    super.characters(ch, start, length);
                }
            }

            @Override
            public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
                if (inTarget) {
                    super.ignorableWhitespace(ch, start, length);
                }
            }
        };

        InputSource source = new InputSource(new StringReader(xml));
        SAXSource saxSource = new SAXSource(filter, source);

        Validator validator = schema.newValidator();
        validator.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException ex) throws SAXException {
                result.addError(new ValidationError(
                        "WARNING", errorPrefix + ex.getMessage(), ex.getLineNumber(), ex.getColumnNumber()));
            }

            @Override
            public void error(SAXParseException ex) throws SAXException {
                result.addError(new ValidationError(
                        "ERROR", errorPrefix + ex.getMessage(), ex.getLineNumber(), ex.getColumnNumber()));
            }

            @Override
            public void fatalError(SAXParseException ex) throws SAXException {
                result.addError(new ValidationError(
                        "FATAL", errorPrefix + ex.getMessage(), ex.getLineNumber(), ex.getColumnNumber()));
                throw ex;
            }
        });

        try {
            validator.validate(saxSource);
        } catch (SAXParseException e) {
            if (result.isValid()) {
                result.addError(new ValidationError(
                        "FATAL", errorPrefix + e.getMessage(), e.getLineNumber(), e.getColumnNumber()));
            }
        } catch (SAXException e) {
            if (result.isValid()) {
                if (e.getCause() instanceof SAXParseException) {
                    SAXParseException spe = (SAXParseException) e.getCause();
                    result.addError(new ValidationError(
                            "FATAL", errorPrefix + spe.getMessage(), spe.getLineNumber(), spe.getColumnNumber()));
                } else {
                    result.addError(new ValidationError("FATAL", errorPrefix + e.getMessage(), -1, -1));
                }
            }
        }
    }
}
