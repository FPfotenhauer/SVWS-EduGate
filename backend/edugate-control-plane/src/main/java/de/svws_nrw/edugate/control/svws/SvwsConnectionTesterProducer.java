package de.svws_nrw.edugate.control.svws;

import de.svws_nrw.edugate.core.svws.DevTrustStoreSslContext;
import de.svws_nrw.edugate.core.svws.HttpSvwsConnectionTester;
import de.svws_nrw.edugate.core.svws.HttpSvwsPrivilegedApiClient;
import de.svws_nrw.edugate.core.svws.SvwsConnectionTester;
import de.svws_nrw.edugate.core.svws.SvwsPrivilegedApiClient;
import de.svws_nrw.edugate.core.svws.SvwsTargetGuard;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.SSLContext;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Stellt die HTTP-Ports gegen SVWS-Instanzen als CDI-Beans bereit: {@link SvwsConnectionTester}
 * (Verbindungstest) und {@link SvwsPrivilegedApiClient} (ADR-014, Privileged-API-Aufrufe).
 * {@code edugate-core} hat bewusst keine Quarkus-Laufzeitabhängigkeiten (siehe README), daher
 * lebt die CDI-Verdrahtung hier in der Control Plane statt direkt an den Implementierungen,
 * analog zu {@link de.svws_nrw.edugate.control.secret.SecretStoreProducer}.
 *
 * <p>Die Umgebungsvariablen {@code EDUGATE_SVWS_DEV_TRUSTSTORE_PATH}/
 * {@code EDUGATE_SVWS_DEV_TRUSTSTORE_PASSWORD} sind ausschließlich für lokale Entwicklung mit
 * selbstsignierten Test-SVWS-Instanzen gedacht (siehe
 * {@link DevTrustStoreSslContext}, `docker-compose.yml`). Sind sie nicht gesetzt, verwendet der
 * Connection-Tester die Standard-JVM-Vertrauenskette - unverändertes Produktionsverhalten.
 */
@ApplicationScoped
public class SvwsConnectionTesterProducer {

    @ConfigProperty(name = "edugate.svws.allowed-networks", defaultValue = "")
    String allowedNetworksConfig;

    @Produces
    @ApplicationScoped
    public SvwsConnectionTester svwsConnectionTester() {
        final SvwsTargetGuard targetGuard = buildTargetGuard();
        final SSLContext sslContext = devTrustStoreSslContext();
        return sslContext == null
            ? new HttpSvwsConnectionTester(null, targetGuard)
            : new HttpSvwsConnectionTester(sslContext, targetGuard);
    }

    /**
     * Stellt den {@link SvwsPrivilegedApiClient}-Port als CDI-Bean bereit (ADR-014: "Alle echten
     * SVWS-Aufrufe laufen über dedizierte Ports/Adapter"). Teilt sich Ziel-Guard und optionalen
     * Dev-Truststore mit {@link #svwsConnectionTester()}, da beide Ports gegen dieselben
     * SVWS-Instanzen sprechen.
     */
    @Produces
    @ApplicationScoped
    public SvwsPrivilegedApiClient svwsPrivilegedApiClient() {
        final SvwsTargetGuard targetGuard = buildTargetGuard();
        final SSLContext sslContext = devTrustStoreSslContext();
        return new HttpSvwsPrivilegedApiClient(sslContext, targetGuard);
    }

    /**
     * Siehe {@link HttpSvwsConnectionTester#HttpSvwsConnectionTester(SSLContext)}: {@code null}
     * verwendet die Standard-JVM-Vertrauenskette (Produktionsfall), ein gesetzter Pfad vertraut
     * zusätzlich einem lokalen Dev-Truststore für selbstsignierte Test-SVWS-Instanzen.
     */
    private SSLContext devTrustStoreSslContext() {
        final String truststorePath = System.getenv("EDUGATE_SVWS_DEV_TRUSTSTORE_PATH");
        if (truststorePath == null || truststorePath.isBlank()) {
            return null;
        }
        final String password = System.getenv("EDUGATE_SVWS_DEV_TRUSTSTORE_PASSWORD");
        return DevTrustStoreSslContext.build(
            Path.of(truststorePath), password == null ? new char[0] : password.toCharArray());
    }

    /**
     * SSRF-Schutz (Issue #17): {@code edugate.svws.allowed-networks} ist eine kommaseparierte
     * Liste erlaubter CIDR-Zielnetze für Verbindungstests. Ohne Konfiguration gilt
     * {@link SvwsTargetGuard#defaultDeny()} - siehe dortige Javadoc für die Begründung. In
     * Dev/Test setzt {@code application.properties} einen permissiven Default, damit lokale
     * SVWS-Testinstanzen (auch im privaten Docker-Netz) ohne weitere Konfiguration erreichbar
     * bleiben.
     */
    private SvwsTargetGuard buildTargetGuard() {
        if (allowedNetworksConfig == null || allowedNetworksConfig.isBlank()) {
            return SvwsTargetGuard.defaultDeny();
        }
        final List<String> networks = Arrays.stream(allowedNetworksConfig.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toList();
        return SvwsTargetGuard.withAllowedNetworks(networks);
    }
}
