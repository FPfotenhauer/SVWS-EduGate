package de.svws_nrw.edugate.core.svws;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Nur für lokale Entwicklung/Tests: erweitert die Standard-JVM-Vertrauenskette (Systemstammzertifikate)
 * <b>additiv</b> um Zertifikate aus einem zusätzlichen Truststore - typischerweise das
 * selbstsignierte Zertifikat einer lokalen SVWS-Testinstanz. Ersetzt niemals die
 * Standardvalidierung und vertraut niemals pauschal jedem Zertifikat: Ein Server-Zertifikat muss
 * entweder von einer System-CA signiert sein <b>oder</b> explizit im zusätzlichen Truststore
 * stehen.
 *
 * <p>In Produktion nicht verwenden - dort sollten SVWS-Instanzen ordentlich signierte oder
 * anderweitig korrekt vertrauenswürdige Zertifikate verwenden (siehe ADR-007, geplantes mTLS).
 */
public final class DevTrustStoreSslContext {

    private DevTrustStoreSslContext() {
    }

    public static SSLContext build(final Path truststorePath, final char[] truststorePassword) {
        try {
            final KeyStore additional = KeyStore.getInstance("PKCS12");
            try (FileInputStream in = new FileInputStream(truststorePath.toFile())) {
                additional.load(in, truststorePassword);
            }

            final X509TrustManager defaultTrustManager = systemTrustManager(null);
            final X509TrustManager additionalTrustManager = systemTrustManager(additional);
            final X509TrustManager combined = combine(defaultTrustManager, additionalTrustManager);

            final SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[] {combined}, null);
            return context;
        } catch (final GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Dev-Truststore konnte nicht geladen werden: " + truststorePath, e);
        }
    }

    private static X509TrustManager combine(final X509TrustManager first, final X509TrustManager second) {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(final X509Certificate[] chain, final String authType) {
                throw new UnsupportedOperationException("Nur für Server-Zertifikatsprüfung vorgesehen.");
            }

            @Override
            public void checkServerTrusted(final X509Certificate[] chain, final String authType)
                    throws CertificateException {
                try {
                    first.checkServerTrusted(chain, authType);
                } catch (final CertificateException e) {
                    second.checkServerTrusted(chain, authType);
                }
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return first.getAcceptedIssuers();
            }
        };
    }

    private static X509TrustManager systemTrustManager(final KeyStore keyStore) throws GeneralSecurityException {
        final TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(keyStore);
        for (final TrustManager trustManager : factory.getTrustManagers()) {
            if (trustManager instanceof X509TrustManager x509TrustManager) {
                return x509TrustManager;
            }
        }
        throw new IllegalStateException("Kein X509TrustManager gefunden.");
    }
}
