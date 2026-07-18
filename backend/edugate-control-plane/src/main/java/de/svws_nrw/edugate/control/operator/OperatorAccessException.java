package de.svws_nrw.edugate.control.operator;

/** Technischer Fehler beim Zugriff über {@link OperatorAccess} (z. B. SQLException). */
public class OperatorAccessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OperatorAccessException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
