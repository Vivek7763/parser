package com.prowidesoftware.swift.model.mx.validation;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable value object representing the identity of an ISO 20022 message, parsed from its XML namespace URI.
 *
 * <p>Standard ISO 20022 namespaces follow the form:
 * {@code urn:iso:std:iso:20022:tech:xsd:{businessArea}.{messageFunction}.{variant}.{version}}
 *
 * <p>Example: {@code urn:iso:std:iso:20022:tech:xsd:pacs.008.001.07}
 * → businessArea=pacs, messageFunction=008, variant=001, version=07
 *
 * <p>This class has zero dependency on Prowide internals.
 */
public final class ISOMessageIdentifier {

    /** ISO 20022 standard URN prefix. */
    public static final String ISO20022_URN_PREFIX = "urn:iso:std:iso:20022:tech:xsd:";

    /**
     * Pattern matching the standard ISO 20022 message-type token
     * {@code {businessArea}.{messageFunction}.{variant}.{version}}.
     */
    private static final Pattern ID_PATTERN =
            Pattern.compile("([a-zA-Z][a-zA-Z0-9]{1,3})\\.([0-9]{3})\\.([0-9]{3})\\.([0-9]{2,3})");

    private final String namespace;
    private final String fullMessageType;
    private final String businessArea;
    private final String messageFunction;
    private final String variant;
    private final String version;

    /**
     * Constructs an identifier from a namespace URI and the extracted type token.
     * Callers should prefer {@link #fromNamespace(String)}.
     */
    public ISOMessageIdentifier(String namespace, String fullMessageType) {
        this.namespace = namespace;
        this.fullMessageType = fullMessageType;

        Matcher matcher = ID_PATTERN.matcher(fullMessageType != null ? fullMessageType : "");
        if (matcher.matches()) {
            this.businessArea = matcher.group(1);
            this.messageFunction = matcher.group(2);
            this.variant = matcher.group(3);
            this.version = matcher.group(4);
        } else {
            this.businessArea = null;
            this.messageFunction = null;
            this.variant = null;
            this.version = null;
        }
    }

    /**
     * Creates an {@link ISOMessageIdentifier} from a raw XML namespace string.
     *
     * <p>Strips the {@code urn:iso:std:iso:20022:tech:xsd:} prefix when present, then parses the remaining token
     * into its constituent parts. Returns {@code null} if the input is {@code null}.
     */
    public static ISOMessageIdentifier fromNamespace(String namespace) {
        if (namespace == null) {
            return null;
        }
        String trimmed = namespace.trim();
        String token = trimmed;
        if (token.startsWith(ISO20022_URN_PREFIX)) {
            token = token.substring(ISO20022_URN_PREFIX.length());
        }
        return new ISOMessageIdentifier(trimmed, token);
    }

    /**
     * Returns {@code true} when all four parts of the message type (businessArea, messageFunction, variant, version)
     * were successfully parsed. An identifier may be non-null but structurally invalid when the namespace does not
     * follow the standard ISO 20022 form.
     */
    public boolean isWellFormed() {
        return businessArea != null && messageFunction != null && variant != null && version != null;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getFullMessageType() {
        return fullMessageType;
    }

    public String getBusinessArea() {
        return businessArea;
    }

    public String getMessageFunction() {
        return messageFunction;
    }

    public String getVariant() {
        return variant;
    }

    public String getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ISOMessageIdentifier that = (ISOMessageIdentifier) o;
        return Objects.equals(fullMessageType, that.fullMessageType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullMessageType);
    }

    @Override
    public String toString() {
        return fullMessageType != null ? fullMessageType : "(unrecognised)";
    }
}
