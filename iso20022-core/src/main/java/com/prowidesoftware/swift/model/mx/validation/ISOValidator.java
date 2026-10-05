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
        if (identifier == null) {
            throw new IllegalArgumentException("identifier must not be null");
        }

        ValidationResult result = new ValidationResult(identifier.getFullMessageType());

        // Throws SchemaNotFoundException when not registered — typed, no string matching needed
        Schema schema = SchemaRegistry.resolve(identifier);

        XMLReader baseReader = SafeXmlUtils.reader(true, null);

        /*
         * XMLFilter that forwards only the <Document> subtree to the validator.
         * Namespace prefix mappings from ancestor elements are forwarded regardless of the active
         * "inDocument" flag so that prefix declarations from wrapper elements are still visible.
         */
        XMLFilter documentFilter = new XMLFilterImpl(baseReader) {
            private boolean inDocument = false;
            private int depth = 0; // depth within Document subtree

            @Override
            public void startPrefixMapping(String prefix, String uri) throws SAXException {
                // Always forward prefix mappings — they may be declared on ancestor wrapper elements
                super.startPrefixMapping(prefix, uri);
            }

            @Override
            public void endPrefixMapping(String prefix) throws SAXException {
                super.endPrefixMapping(prefix);
            }

            @Override
            public void startElement(String uri, String localName, String qName, org.xml.sax.Attributes atts)
                    throws SAXException {
                if (!inDocument && "Document".equals(localName)) {
                    inDocument = true;
                }
                if (inDocument) {
                    depth++;
                    super.startElement(uri, localName, qName, atts);
                }
            }

            @Override
            public void endElement(String uri, String localName, String qName) throws SAXException {
                if (inDocument) {
                    super.endElement(uri, localName, qName);
                    depth--;
                    if (depth == 0) {
                        inDocument = false;
                    }
                }
            }

            @Override
            public void characters(char[] ch, int start, int length) throws SAXException {
                if (inDocument) {
                    super.characters(ch, start, length);
                }
            }

            @Override
            public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
                if (inDocument) {
                    super.ignorableWhitespace(ch, start, length);
                }
            }
        };

        InputSource source = new InputSource(new StringReader(xml));
        SAXSource saxSource = new SAXSource(documentFilter, source);

        Validator validator = schema.newValidator();
        validator.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException ex) throws SAXException {
                result.addError(
                        new ValidationError("WARNING", ex.getMessage(), ex.getLineNumber(), ex.getColumnNumber()));
            }

            @Override
            public void error(SAXParseException ex) throws SAXException {
                // Do not re-throw: accumulate all errors in one pass
                result.addError(
                        new ValidationError("ERROR", ex.getMessage(), ex.getLineNumber(), ex.getColumnNumber()));
            }

            @Override
            public void fatalError(SAXParseException ex) throws SAXException {
                result.addError(
                        new ValidationError("FATAL", ex.getMessage(), ex.getLineNumber(), ex.getColumnNumber()));
                // Fatal errors indicate the stream is unrecoverable; re-throw to terminate parsing
                throw ex;
            }
        });

        try {
            validator.validate(saxSource);
        } catch (SAXParseException e) {
            // Fatal parse error occurred (e.g. truncated or malformed XML tags).
            // ErrorHandler.fatalError() already added the ValidationError("FATAL", ...) to result before re-throwing.
            // If for any reason result is still empty, add it defensively now.
            if (result.isValid()) {
                result.addError(new ValidationError("FATAL", e.getMessage(), e.getLineNumber(), e.getColumnNumber()));
            }
        } catch (SAXException e) {
            if (result.isValid()) {
                if (e.getCause() instanceof SAXParseException) {
                    SAXParseException spe = (SAXParseException) e.getCause();
                    result.addError(
                            new ValidationError("FATAL", spe.getMessage(), spe.getLineNumber(), spe.getColumnNumber()));
                } else {
                    result.addError(new ValidationError("FATAL", e.getMessage(), -1, -1));
                }
            }
        }
        return result;
    }
}
