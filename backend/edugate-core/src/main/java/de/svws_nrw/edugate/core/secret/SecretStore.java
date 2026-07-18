package de.svws_nrw.edugate.core.secret;

/**
 * Port für die Ver- und Entschlüsselung von SVWS-Zugangsdaten (ADR-006).
 * Implementierungen dürfen Klartext niemals loggen oder länger als für den
 * unmittelbaren SVWS-Aufruf nötig im Speicher halten.
 */
public interface SecretStore {

    /**
     * Verschlüsselt {@code plaintext} und liefert ein selbstbeschreibendes Chiffrat
     * (enthält Schlüsselversion und Nonce, siehe {@link #keyVersion()}).
     */
    byte[] encrypt(byte[] plaintext);

    /**
     * Entschlüsselt ein zuvor mit {@link #encrypt(byte[])} erzeugtes Chiffrat.
     *
     * @throws SecretStoreException wenn das Chiffrat manipuliert, beschädigt oder
     *      mit einer unbekannten Schlüsselversion kodiert wurde.
     */
    byte[] decrypt(byte[] encoded);

    /** Version des aktuell für {@link #encrypt(byte[])} verwendeten Schlüssels. */
    int keyVersion();
}
