package com.prowidesoftware.swift.model.mx.validation;

/**
 * A single XSD constraint violation reported during {@link ISOValidator} schema validation.
 *
 * <p>Line and column numbers map to the original XML source positions as reported by the SAX parser.
 * They are {@code -1} when the underlying SAX exception did not include positional information.
 */
public final class ValidationError {

    /** Severity values matching the SAX {@link org.xml.sax.ErrorHandler} callbacks. */
    public static final String WARNING = "WARNING";

    public static final String ERROR = "ERROR";
    public static final String FATAL = "FATAL";

    private final String severity;
    private final String message;
    private final int line;
    private final int column;

    public ValidationError(String severity, String message, int line, int column) {
        this.severity = severity;
        this.message = message;
        this.line = line;
        this.column = column;
    }

    public String getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    /** Source line number, or {@code -1} when unavailable. */
    public int getLine() {
        return line;
    }

    /** Source column number, or {@code -1} when unavailable. */
    public int getColumn() {
        return column;
    }

    @Override
    public String toString() {
        return severity + " at [" + line + ":" + column + "]: " + message;
    }
}
