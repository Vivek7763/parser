package com.prowidesoftware.swift.model.mx.validation;

import com.prowidesoftware.swift.utils.SafeXmlUtils;
import java.io.StringReader;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Safely parses XML to extract structural metadata such as Document namespace and AppHdr MsgDefIdr.
 */
public class MessageStructureInfo {
    private final ISOMessageIdentifier documentIdentifier;
    private final ISOMessageIdentifier appHdrIdentifier;
    private final String msgDefIdr;

    public MessageStructureInfo(
            ISOMessageIdentifier documentIdentifier, ISOMessageIdentifier appHdrIdentifier, String msgDefIdr) {
        this.documentIdentifier = documentIdentifier;
        this.appHdrIdentifier = appHdrIdentifier;
        this.msgDefIdr = msgDefIdr;
    }

    public ISOMessageIdentifier getDocumentIdentifier() {
        return documentIdentifier;
    }

    public ISOMessageIdentifier getAppHdrIdentifier() {
        return appHdrIdentifier;
    }

    public String getMsgDefIdr() {
        return msgDefIdr;
    }

    public static MessageStructureInfo parse(String xml) {
        if (xml == null || xml.isBlank()) {
            return new MessageStructureInfo(null, null, null);
        }
        try {
            XMLReader reader = SafeXmlUtils.reader(true, null);
            StructureHandler handler = new StructureHandler();
            reader.setContentHandler(handler);
            try {
                reader.parse(new InputSource(new StringReader(xml)));
            } catch (EarlyExitException e) {
                // Expected when we have found everything
            }
            return new MessageStructureInfo(handler.docId, handler.appHdrId, handler.msgDefIdr);
        } catch (Exception e) {
            return new MessageStructureInfo(null, null, null);
        }
    }

    private static class EarlyExitException extends SAXException {
        // Exception to stop parsing once we found Document and AppHdr
    }

    private static class StructureHandler extends DefaultHandler {
        ISOMessageIdentifier docId = null;
        ISOMessageIdentifier appHdrId = null;
        String msgDefIdr = null;
        boolean inMsgDefIdr = false;
        StringBuilder msgDefBuilder = new StringBuilder();

        boolean foundDoc = false;
        boolean foundAppHdr = false;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
            if ("Document".equals(localName)) {
                if (uri != null && !uri.isEmpty()) {
                    docId = ISOMessageIdentifier.fromNamespace(uri);
                }
                foundDoc = true;
                checkExit();
            } else if ("AppHdr".equals(localName)) {
                if (uri != null && !uri.isEmpty()) {
                    appHdrId = ISOMessageIdentifier.fromNamespace(uri);
                }
                foundAppHdr = true;
            } else if (foundAppHdr && "MsgDefIdr".equals(localName)) {
                inMsgDefIdr = true;
                msgDefBuilder.setLength(0);
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (inMsgDefIdr) {
                msgDefBuilder.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (inMsgDefIdr && "MsgDefIdr".equals(localName)) {
                msgDefIdr = msgDefBuilder.toString().trim();
                inMsgDefIdr = false;
                checkExit();
            }
        }

        private void checkExit() throws EarlyExitException {
            if (foundDoc && (appHdrId == null || msgDefIdr != null)) {
                throw new EarlyExitException();
            }
        }
    }
}
