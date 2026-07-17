package de.svws_nrw.edugate.core.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AesGcmSecretStoreTest {

    private AesGcmSecretStore store;

    @BeforeEach
    void setUp() {
        final byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        store = new AesGcmSecretStore(key, 1);
    }

    @Test
    void roundtrip_liefertUrspruenglichenKlartext() {
        final byte[] plaintext = "svws-benutzer:geheimes-passwort".getBytes(StandardCharsets.UTF_8);

        final byte[] encoded = store.encrypt(plaintext);
        final byte[] decrypted = store.decrypt(encoded);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void encrypt_erzeugtUnterschiedlicheChiffrateFuerGleichenKlartext_wegenZufaelligemNonce() {
        final byte[] plaintext = "gleicher-klartext".getBytes(StandardCharsets.UTF_8);

        final byte[] encodedA = store.encrypt(plaintext);
        final byte[] encodedB = store.encrypt(plaintext);

        assertThat(encodedA).isNotEqualTo(encodedB);
    }

    @Test
    void encrypt_kodiertKeyVersionInDenErstenVierBytes() {
        final byte[] encoded = store.encrypt("x".getBytes(StandardCharsets.UTF_8));

        assertThat(encoded[0]).isZero();
        assertThat(encoded[1]).isZero();
        assertThat(encoded[2]).isZero();
        assertThat(encoded[3]).isEqualTo((byte) 1);
    }

    @Test
    void decrypt_erkenntManipuliertesChiffrat_undWirftSecretStoreException() {
        final byte[] encoded = store.encrypt("unverändert bleiben".getBytes(StandardCharsets.UTF_8));
        final byte[] manipuliert = encoded.clone();
        manipuliert[manipuliert.length - 1] ^= 0x01;

        assertThatThrownBy(() -> store.decrypt(manipuliert))
            .isInstanceOf(SecretStoreException.class)
            .hasMessageContaining("manipuliert");
    }

    @Test
    void decrypt_erkenntManipulierteSchluesselversion() {
        final byte[] encoded = store.encrypt("x".getBytes(StandardCharsets.UTF_8));
        final byte[] manipuliert = encoded.clone();
        manipuliert[3] = (byte) 9;

        assertThatThrownBy(() -> store.decrypt(manipuliert))
            .isInstanceOf(SecretStoreException.class)
            .hasMessageContaining("Schlüsselversion");
    }

    @Test
    void decrypt_mitFalschemSchluessel_scheitert() {
        final byte[] encoded = store.encrypt("geheim".getBytes(StandardCharsets.UTF_8));
        final byte[] anderesKey = new byte[32];
        new SecureRandom().nextBytes(anderesKey);
        final AesGcmSecretStore fremderStore = new AesGcmSecretStore(anderesKey, 1);

        assertThatThrownBy(() -> fremderStore.decrypt(encoded))
            .isInstanceOf(SecretStoreException.class);
    }

    @Test
    void konstruktor_lehntFalscheSchluessellaengeAb() {
        assertThatThrownBy(() -> new AesGcmSecretStore(new byte[16], 1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromEnvironment_ohneGesetzteVariable_wirftIllegalStateException() {
        assertThat(System.getenv(AesGcmSecretStore.MASTER_KEY_ENV)).isNull();
        assertThatThrownBy(AesGcmSecretStore::fromEnvironment)
            .isInstanceOf(IllegalStateException.class);
    }
}
