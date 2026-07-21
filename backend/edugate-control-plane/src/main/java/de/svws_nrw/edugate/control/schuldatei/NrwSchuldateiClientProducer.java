package de.svws_nrw.edugate.control.schuldatei;

import de.svws_nrw.edugate.core.schuldatei.HttpNrwSchuldateiClient;
import de.svws_nrw.edugate.core.schuldatei.NrwSchuldateiClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

/**
 * Stellt den {@link NrwSchuldateiClient}-Port als CDI-Bean bereit. {@code edugate-core} hat
 * bewusst keine Quarkus-Laufzeitabhängigkeiten (siehe README), daher lebt die CDI-Verdrahtung
 * hier in der Control Plane, analog zu {@link de.svws_nrw.edugate.control.svws.SvwsConnectionTesterProducer}.
 */
@ApplicationScoped
public class NrwSchuldateiClientProducer {

    @Produces
    @ApplicationScoped
    public NrwSchuldateiClient nrwSchuldateiClient() {
        return new HttpNrwSchuldateiClient();
    }
}
