package de.svws_nrw.edugate.core.secret;

/** Signalisiert einen Fehler bei der Ver-/Entschlüsselung, insbesondere ein manipuliertes Chiffrat. */
public class SecretStoreException extends RuntimeException {

    public SecretStoreException(String message) {
        super(message);
    }

    public SecretStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
