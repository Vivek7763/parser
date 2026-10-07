package com.prowidesoftware.swift.model.mx.validation;

import java.io.InputStream;
import java.io.Reader;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

public class ClasspathResourceResolver implements LSResourceResolver {

    @Override
    public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId, String baseURI) {
        if (systemId != null
                && (systemId.startsWith("http://")
                        || systemId.startsWith("https://")
                        || systemId.startsWith("file://"))) {
            // Block external remote resolution for security
            return null;
        }

        // We assume included schemas are placed in the same classpath location or a specific one.
        // For testing, let's just lookup by systemId from root classpath.
        String path = systemId.startsWith("/") ? systemId : "/schemas/" + systemId;
        InputStream is = getClass().getResourceAsStream(path);
        if (is != null) {
            return new LSInputImpl(publicId, systemId, is);
        }
        return null;
    }

    private static class LSInputImpl implements LSInput {
        private String publicId;
        private String systemId;
        private InputStream byteStream;

        public LSInputImpl(String publicId, String systemId, InputStream byteStream) {
            this.publicId = publicId;
            this.systemId = systemId;
            this.byteStream = byteStream;
        }

        @Override
        public Reader getCharacterStream() {
            return null;
        }

        @Override
        public void setCharacterStream(Reader characterStream) {}

        @Override
        public InputStream getByteStream() {
            return byteStream;
        }

        @Override
        public void setByteStream(InputStream byteStream) {}

        @Override
        public String getStringData() {
            return null;
        }

        @Override
        public void setStringData(String stringData) {}

        @Override
        public String getSystemId() {
            return systemId;
        }

        @Override
        public void setSystemId(String systemId) {}

        @Override
        public String getPublicId() {
            return publicId;
        }

        @Override
        public void setPublicId(String publicId) {}

        @Override
        public String getBaseURI() {
            return null;
        }

        @Override
        public void setBaseURI(String baseURI) {}

        @Override
        public String getEncoding() {
            return null;
        }

        @Override
        public void setEncoding(String encoding) {}

        @Override
        public boolean getCertifiedText() {
            return false;
        }

        @Override
        public void setCertifiedText(boolean certifiedText) {}
    }
}
