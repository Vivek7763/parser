package com.prowidesoftware.swift.model.mx.validation;

import com.prowidesoftware.swift.model.MxId;
import com.prowidesoftware.swift.model.mx.AbstractMX;
import com.prowidesoftware.swift.model.mx.MxParseUtils;
import java.util.Optional;

/**
 * Thin adapter isolating all direct Prowide API calls from the FSS orchestration layer.
 *
 * <p>By confining Prowide imports to this class, the surrounding pipeline ({@link ISOParser},
 * {@link ISOValidator}, {@link SchemaRegistry}, {@link ISOParserResult}) has <em>zero</em> compile-time
 * dependency on Prowide. Replacing Prowide in the future requires changes only here and in
 * {@code ISOParserResult} (which references {@code AbstractMX}).
 *
 * <h2>Known Prowide limitation</h2>
 * {@link AbstractMX#parse(String)} returns {@code null} both when the generated model class is absent
 * ({@code ClassNotFoundException}) and when JAXB parsing fails internally. The two cannot be distinguished
 * via the public Prowide API, so callers must accept the combined status
 * {@link ISOParserResult.ModelParsingStatus#PROWIDE_MODEL_UNAVAILABLE_OR_PARSE_ERROR}.
 */
public final class ProwideAdapter {

    private ProwideAdapter() {}

    /**
     * Extracts the ISO message identity from the XML using Prowide's lightweight SAX namespace reader.
     *
     * <p>This method does <em>not</em> trigger generated-class reflection or JAXB unmarshalling. It is safe to
     * call on any XML, including messages whose generated models are absent.
     *
     * @param xml the raw XML string
     * @return the message identifier, or {@code null} when the namespace cannot be determined
     */
    public static ISOMessageIdentifier extractIdentifier(String xml) {
        if (xml == null || xml.isBlank()) {
            return null;
        }
        try {
            Optional<MxId> mxIdOpt = MxParseUtils.identifyMessage(xml);
            if (mxIdOpt.isPresent()) {
                // MxId.id() returns the full type token (e.g. "pacs.008.001.14")
                return ISOMessageIdentifier.fromNamespace(mxIdOpt.get().id());
            }
        } catch (Exception e) {
            // Prowide can throw on severely malformed XML; treat as unidentifiable
        }
        return null;
    }

    // Test spy counter to verify parseToModel is never called on validation failure
    public static final java.util.concurrent.atomic.AtomicInteger parseToModelCallCount =
            new java.util.concurrent.atomic.AtomicInteger();

    public static int getParseToModelCallCount() {
        return parseToModelCallCount.get();
    }

    public static void resetParseToModelCallCount() {
        parseToModelCallCount.set(0);
    }

    /**
     * Delegates JAXB model parsing to Prowide.
     *
     * <p>Returns the parsed {@link AbstractMX} model, or {@code null} when Prowide cannot produce one (either
     * because the generated class does not exist in this deployment or because of an internal parsing error).
     *
     * @param xml the raw XML string
     * @return the parsed model, or {@code null}
     */
    public static AbstractMX parseToModel(String xml) {
        parseToModelCallCount.incrementAndGet();
        if (xml == null || xml.isBlank()) {
            return null;
        }
        try {
            return AbstractMX.parse(xml);
        } catch (Exception e) {
            return null;
        }
    }
}
