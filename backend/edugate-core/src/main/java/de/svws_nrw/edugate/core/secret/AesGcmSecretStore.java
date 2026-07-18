package de.svws_nrw.edugate.core.secret;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM-Implementierung des {@link SecretStore}-Ports (ADR-006, Phase 1).
 *
 * <p>Chiffrat-Layout: {@code [keyVersion:4 Byte][nonce:12 Byte][ciphertext+tag]}.
 * Der Nonce ist je Aufruf von {@link #encrypt(byte[])} zufällig; die Schlüsselversion
 * ist im Chiffrat kodiert, damit spätere Key-Rotation ein Re-Encrypt-Verfahren anhand
 * der Version steuern kann.
 */
public final class AesGcmSecretStore implements SecretStore {

    public static final String MASTER_KEY_ENV = "EDUGATE_MASTER_KEY";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int NONCE_LENGTH_BYTES = 12;
    private static final int KEY_VERSION_LENGTH_BYTES = Integer.BYTES;

    private final SecretKey key;
    private final int keyVersion;
    private final SecureRandom random = new SecureRandom();

    public AesGcmSecretStore(final byte[] rawKey, final int keyVersion) {
        if (rawKey.length != KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException(
                "Master-Key muss %d Byte lang sein (AES-256), war: %d Byte.".formatted(KEY_LENGTH_BYTES, rawKey.length));
        }
        this.key = new SecretKeySpec(rawKey, "AES");
        this.keyVersion = keyVersion;
    }

    /**
     * Liest den Base64-kodierten Master-Key aus der Umgebungsvariable {@value #MASTER_KEY_ENV}
     * (Container-Secret gemäß ADR-006 – niemals im Repo oder in {@code .env.example} mit echtem Wert).
     */
    public static AesGcmSecretStore fromEnvironment() {
        final String encoded = System.getenv(MASTER_KEY_ENV);
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException("Umgebungsvariable %s ist nicht gesetzt.".formatted(MASTER_KEY_ENV));
        }
        return new AesGcmSecretStore(Base64.getDecoder().decode(encoded), 1);
    }

    @Override
    public byte[] encrypt(final byte[] plaintext) {
        final byte[] nonce = new byte[NONCE_LENGTH_BYTES];
        random.nextBytes(nonce);
        try {
            final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            final byte[] ciphertext = cipher.doFinal(plaintext);

            final ByteBuffer buffer = ByteBuffer.allocate(KEY_VERSION_LENGTH_BYTES + NONCE_LENGTH_BYTES + ciphertext.length);
            buffer.putInt(keyVersion);
            buffer.put(nonce);
            buffer.put(ciphertext);
            return buffer.array();
        } catch (final GeneralSecurityException e) {
            throw new SecretStoreException("Verschlüsselung fehlgeschlagen.", e);
        }
    }

    @Override
    public byte[] decrypt(final byte[] encoded) {
        if (encoded.length < KEY_VERSION_LENGTH_BYTES + NONCE_LENGTH_BYTES) {
            throw new SecretStoreException("Chiffrat ist unvollständig.");
        }
        final ByteBuffer buffer = ByteBuffer.wrap(encoded);
        final int encodedKeyVersion = buffer.getInt();
        if (encodedKeyVersion != keyVersion) {
            throw new SecretStoreException("Unbekannte Schlüsselversion im Chiffrat: " + encodedKeyVersion);
        }
        final byte[] nonce = new byte[NONCE_LENGTH_BYTES];
        buffer.get(nonce);
        final byte[] ciphertext = new byte[buffer.remaining()];
        buffer.get(ciphertext);

        try {
            final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            return cipher.doFinal(ciphertext);
        } catch (final AEADBadTagException e) {
            throw new SecretStoreException("Chiffrat wurde manipuliert oder ist beschädigt (GCM-Tag ungültig).", e);
        } catch (final GeneralSecurityException e) {
            throw new SecretStoreException("Entschlüsselung fehlgeschlagen.", e);
        }
    }

    @Override
    public int keyVersion() {
        return keyVersion;
    }
}
