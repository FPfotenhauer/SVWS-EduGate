package de.svws_nrw.edugate.control.svws;

import de.svws_nrw.edugate.core.svws.DevTrustStoreSslContext;
import de.svws_nrw.edugate.core.svws.HttpSvwsConnectionTester;
import de.svws_nrw.edugate.core.svws.SvwsConnectionTester;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.nio.file.Path;
import javax.net.ssl.SSLContext;

/**
 * Stellt den {@link SvwsConnectionTester}-Port als CDI-Bean bereit. {@code edugate-core} hat
 * bewusst keine Quarkus-Laufzeitabhängigkeiten (siehe README), daher lebt die CDI-Verdrahtung
 * hier in der Control Plane statt direkt an {@link HttpSvwsConnectionTester}, analog zu
 * {@link de.svws_nrw.edugate.control.secret.SecretStoreProducer}.
 *
 * <p>Die Umgebungsvariablen {@code EDUGATE_SVWS_DEV_TRUSTSTORE_PATH}/
 * {@code EDUGATE_SVWS_DEV_TRUSTSTORE_PASSWORD} sind ausschließlich für lokale Entwicklung mit
 * selbstsignierten Test-SVWS-Instanzen gedacht (siehe
 * {@link DevTrustStoreSslContext}, `docker-compose.yml`). Sind sie nicht gesetzt, verwendet der
 * Connection-Tester die Standard-JVM-Vertrauenskette - unverändertes Produktionsverhalten.
 */
@ApplicationScoped
public class SvwsConnectionTesterProducer {

    @Produces
    @ApplicationScoped
    public SvwsConnectionTester svwsConnectionTester() {
        final String truststorePath = System.getenv("EDUGATE_SVWS_DEV_TRUSTSTORE_PATH");
        if (truststorePath == null || truststorePath.isBlank()) {
            return new HttpSvwsConnectionTester();
        }

        final String password = System.getenv("EDUGATE_SVWS_DEV_TRUSTSTORE_PASSWORD");
        final SSLContext sslContext = DevTrustStoreSslContext.build(
            Path.of(truststorePath), password == null ? new char[0] : password.toCharArray());
        return new HttpSvwsConnectionTester(sslContext);
    }
}
