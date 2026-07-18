package de.svws_nrw.edugate.control.svws;

import de.svws_nrw.edugate.core.svws.HttpSvwsConnectionTester;
import de.svws_nrw.edugate.core.svws.SvwsConnectionTester;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Stellt den {@link SvwsConnectionTester}-Port als CDI-Bean bereit. {@code edugate-core} hat
 * bewusst keine Quarkus-Laufzeitabhängigkeiten (siehe README), daher lebt die CDI-Verdrahtung
 * hier in der Control Plane statt direkt an {@link HttpSvwsConnectionTester}, analog zu
 * {@link de.svws_nrw.edugate.control.secret.SecretStoreProducer}.
 */
@ApplicationScoped
public class SvwsConnectionTesterProducer {

    @Produces
    @ApplicationScoped
    public SvwsConnectionTester svwsConnectionTester() {
        return new HttpSvwsConnectionTester();
    }
}
