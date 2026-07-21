package de.svws_nrw.edugate.core.svws;

/** Wird von {@link MinimalJsonParser} bei syntaktisch ungültiger Eingabe geworfen. */
public class JsonParseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public JsonParseException(final String message) {
        super(message);
    }
}
