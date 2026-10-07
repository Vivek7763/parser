package com.prowidesoftware.swift.model.mx.validation;

import com.prowidesoftware.swift.utils.SafeXmlUtils;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

/**
 * Registry that resolves a compiled {@link javax.xml.validation.Schema} from an {@link ISOMessageIdentifier}.
 *
 * <h2>Schema layout convention</h2>
 * Schemas are resolved from the classpath or from a configurable base directory
 * (default: {@value #DEFAULT_SCHEMAS_BASE_DIR}) in the following structure:
 * <pre>
 * {baseDir}/{businessArea}/{messageType}/{messageType}.xsd
 * e.g.  schemas/pacs/pacs.008.001.14/pacs.008.001.14.xsd
 * </pre>
 *
 * <h2>Path resolution priority</h2>
 * 1. <b>Classpath:</b> The registry attempts to load the XSD as a classpath resource via
 *    {@link Class#getResource(String)} using the absolute path format {@code /schemas/...}.
 * 2. <b>Filesystem Override:</b> If not found on the classpath, it tries the configured
 *    base directory first, then a sibling {@code ../{baseDir}} path, making it work both
 *    from the project root (Gradle) and from the {@code iso20022-core} module directory.
 *
 * <h2>Caching</h2>
 * Compiled {@link Schema} objects are cached by message-type string after the first successful load. The cache is
 * process-scoped (static) and thread-safe.
 *
 * <h2>Extensibility</h2>
 * The base directory can be changed at runtime via {@link #setBaseSchemasDir(String)}, allowing the registry to
 * be pointed at an external directory without code changes.
 */
public final class SchemaRegistry {

    /** Default relative path for the schema root directory. */
    public static final String DEFAULT_SCHEMAS_BASE_DIR = "schemas";

    private static volatile String baseSchemasDir = DEFAULT_SCHEMAS_BASE_DIR;
    private static final Map<String, Schema> schemaCache = new ConcurrentHashMap<>();

    private SchemaRegistry() {}

    /**
     * Changes the base directory used for schema resolution. Clears the compiled-schema cache.
     *
     * @param dir path to the directory that contains the {@code {businessArea}/} sub-directories
     */
    public static void setBaseSchemasDir(String dir) {
        baseSchemasDir = dir;
        schemaCache.clear();
    }

    /**
     * Resolves a {@link Schema} for the given message identifier.
     *
     * @param identifier the message whose XSD should be located
     * @return a compiled {@link Schema}
     * @throws SchemaNotFoundException  when no XSD file is found for the identifier
     * @throws SchemaCompilationException when the XSD file exists but cannot be compiled
     */
    public static Schema resolve(ISOMessageIdentifier identifier) {
        if (identifier == null) {
            throw new SchemaNotFoundException("(null identifier)");
        }
        return resolve(identifier.getFullMessageType());
    }

    /**
     * Resolves a {@link Schema} by message-type string or namespace URI.
     *
     * @param namespaceOrId either the full namespace URI ({@code urn:iso:std:iso:20022:tech:xsd:pacs.008.001.14})
     *                      or just the type token ({@code pacs.008.001.14})
     * @return a compiled {@link Schema}
     * @throws SchemaNotFoundException    when no XSD file is found
     * @throws SchemaCompilationException when the XSD exists but cannot be compiled
     */
    public static Schema resolve(String namespaceOrId) {
        String identifier = normalise(namespaceOrId);

        // Cache hit — avoids re-compiling the XSD on every invocation
        Schema cached = schemaCache.get(identifier);
        if (cached != null) {
            return cached;
        }

        String category = extractCategory(identifier);

        // Candidate 1: Classpath Resource (Primary)
        String resourcePath = "/schemas/" + category + "/" + identifier + "/" + identifier + ".xsd";
        java.net.URL schemaUrl = SchemaRegistry.class.getResource(resourcePath);

        if (schemaUrl != null) {
            try {
                SchemaFactory sf = SafeXmlUtils.schemaFactory();
                sf.setResourceResolver(new ClasspathResourceResolver());
                Schema schema = sf.newSchema(schemaUrl);
                schemaCache.put(identifier, schema);
                return schema;
            } catch (Exception e) {
                throw new SchemaCompilationException(identifier, e);
            }
        }

        // Candidate 2: Filesystem Override (Fallback)
        File schemaFile = locateFile(category, identifier);

        if (schemaFile == null || !schemaFile.exists()) {
            throw new SchemaNotFoundException(identifier);
        }

        try {
            SchemaFactory sf = SafeXmlUtils.schemaFactory();
            sf.setResourceResolver(new ClasspathResourceResolver());
            Schema schema = sf.newSchema(schemaFile);
            schemaCache.put(identifier, schema);
            return schema;
        } catch (Exception e) {
            throw new SchemaCompilationException(identifier, e);
        }
    }

    /** Returns {@code true} when an XSD is registered for the given identifier. Does not throw. */
    public static boolean isRegistered(ISOMessageIdentifier identifier) {
        if (identifier == null) {
            return false;
        }
        try {
            resolve(identifier);
            return true;
        } catch (SchemaNotFoundException | SchemaCompilationException e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static String normalise(String namespaceOrId) {
        if (namespaceOrId == null) {
            throw new SchemaNotFoundException("(null)");
        }
        String id = namespaceOrId;
        if (id.startsWith(ISOMessageIdentifier.ISO20022_URN_PREFIX)) {
            id = id.substring(ISOMessageIdentifier.ISO20022_URN_PREFIX.length());
        }
        return id;
    }

    private static String extractCategory(String identifier) {
        if (identifier.contains(".")) {
            return identifier.split("\\.")[0];
        }
        // Defensive fall-back: use first 4 chars
        return identifier.length() >= 4 ? identifier.substring(0, 4) : identifier;
    }

    private static File locateFile(String category, String identifier) {
        // Candidate 1: relative to CWD — works from project root
        Path candidate1 = Paths.get(baseSchemasDir, category, identifier, identifier + ".xsd");
        if (candidate1.toFile().exists()) {
            return candidate1.toFile();
        }

        // Candidate 2: one level up — works from iso20022-core module directory
        Path candidate2 = Paths.get("..", baseSchemasDir, category, identifier, identifier + ".xsd");
        if (candidate2.toFile().exists()) {
            return candidate2.toFile();
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // Typed exceptions (replaces string matching in ISOParser)
    // -------------------------------------------------------------------------

    /** Thrown when no XSD file is available for the requested message type. */
    public static final class SchemaNotFoundException extends RuntimeException {
        public SchemaNotFoundException(String identifier) {
            super("No XSD registered for message type: " + identifier);
        }
    }

    /** Thrown when an XSD file is found but fails to compile as a {@link Schema}. */
    public static final class SchemaCompilationException extends RuntimeException {
        public SchemaCompilationException(String identifier, Throwable cause) {
            super("XSD compilation failed for: " + identifier, cause);
        }
    }
}
