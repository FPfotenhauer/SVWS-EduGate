package de.svws_nrw.edugate.control.secret;

import de.svws_nrw.edugate.core.secret.AesGcmSecretStore;
import de.svws_nrw.edugate.core.secret.SecretStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Stellt den {@link SecretStore}-Port (ADR-006) als CDI-Bean bereit. {@code edugate-core} hat
 * bewusst keine Quarkus-Laufzeitabhängigkeiten (siehe README), daher lebt die CDI-Verdrahtung
 * hier in der Control Plane statt direkt an {@link AesGcmSecretStore}.
 */
@ApplicationScoped
public class SecretStoreProducer {

    @Produces
    @ApplicationScoped
    public SecretStore secretStore() {
        return AesGcmSecretStore.fromEnvironment();
    }
}
