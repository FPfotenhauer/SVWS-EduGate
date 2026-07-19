package de.svws_nrw.edugate.control.schema;

import de.svws_nrw.edugate.control.schema.dto.SchemaOverviewPageDto;
import de.svws_nrw.edugate.core.domain.SchemaStatus;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.UUID;

/**
 * Betreiber-Übersicht "Schuldatenbanken" (ADR-013): mandantenübergreifende, nur lesende Sicht auf
 * Schemata inklusive Schule/Schulträger/Instanz-Kontext. Primärer Einstieg für die fachliche
 * Schuldatenbank-Sicht und - gefiltert nach {@code instanzId} - für die Instanzsicht. Schreibende
 * Einzeloperationen bleiben tenant-gebunden unter {@link SchemaResource}.
 */
@Path("/admin/api/v1/schuldatenbanken")
@RolesAllowed("dienstleister-admin")
@Produces(MediaType.APPLICATION_JSON)
public class SchemaOverviewResource {

    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 100;

    @Inject
    SchemaOverviewService service;

    @Inject
    SecurityIdentity securityIdentity;

    @GET
    public SchemaOverviewPageDto list(
            @QueryParam("page") @DefaultValue("0") final int page,
            @QueryParam("size") @DefaultValue("" + DEFAULT_PAGE_SIZE) final int size,
            @QueryParam("instanzId") final UUID instanzId,
            @QueryParam("schultraegerId") final UUID schultraegerId,
            @QueryParam("umgebung") final String umgebung,
            @QueryParam("status") final SchemaStatus status,
            @QueryParam("q") final String q) {
        final int safePage = Math.max(page, 0);
        final int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return service.list(adminSubject(), safePage, safeSize, instanzId, schultraegerId, umgebung, status, q);
    }

    private String adminSubject() {
        return securityIdentity.getPrincipal().getName();
    }
}
