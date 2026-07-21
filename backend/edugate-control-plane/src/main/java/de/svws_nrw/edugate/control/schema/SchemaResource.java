package de.svws_nrw.edugate.control.schema;

import de.svws_nrw.edugate.control.schema.dto.SchemaCreateRequest;
import de.svws_nrw.edugate.control.schema.dto.SchemaDestroyResultDto;
import de.svws_nrw.edugate.control.schema.dto.SchemaDto;
import de.svws_nrw.edugate.control.schema.dto.SchemaNamingSuggestionDto;
import de.svws_nrw.edugate.control.schema.dto.SchemaUpdateRequest;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

/**
 * Schema-/Schuldatenbank-Verwaltung je Schule (Dienstleister-Admin, ADR-012). Schema bleibt
 * tenant-gebunden (ADR-009/ADR-011), alle Operationen laufen daher über {@link SchemaService}/
 * {@code TenantAccess}, mit zusätzlichem Audit für die als "gefährlich" eingestuften
 * Schreiboperationen (Anlegen, Ändern, Deaktivieren).
 */
@Path("/admin/api/v1/schultraeger/{schultraegerId}/schulen/{schuleId}/schemata")
@RolesAllowed("dienstleister-admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SchemaResource {

    @Inject
    SchemaService service;

    @Inject
    SecurityIdentity securityIdentity;

    @GET
    public List<SchemaDto> list(@PathParam("schultraegerId") final UUID schultraegerId,
            @PathParam("schuleId") final UUID schuleId) {
        return service.list(schultraegerId, schuleId);
    }

    @GET
    @Path("/namensvorschlag")
    public SchemaNamingSuggestionDto namingSuggestion(@PathParam("schultraegerId") final UUID schultraegerId,
            @PathParam("schuleId") final UUID schuleId, @QueryParam("umgebung") final String umgebung) {
        return service.namingSuggestion(schultraegerId, schuleId, umgebung);
    }

    @POST
    public Response create(@PathParam("schultraegerId") final UUID schultraegerId,
            @PathParam("schuleId") final UUID schuleId, @Valid final SchemaCreateRequest request) {
        final SchemaDto created = service.create(adminSubject(), schultraegerId, schuleId, request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    public SchemaDto update(@PathParam("schultraegerId") final UUID schultraegerId,
            @PathParam("schuleId") final UUID schuleId, @PathParam("id") final UUID id,
            @Valid final SchemaUpdateRequest request) {
        return service.update(adminSubject(), schultraegerId, schuleId, id, request);
    }

    @DELETE
    @Path("/{id}")
    public SchemaDto deactivate(@PathParam("schultraegerId") final UUID schultraegerId,
            @PathParam("schuleId") final UUID schuleId, @PathParam("id") final UUID id) {
        return service.deactivate(adminSubject(), schultraegerId, schuleId, id);
    }

    /**
     * Löscht ein echtes Schema unwiderruflich über die SVWS-Privileged-API (ADR-014). Liefert bei
     * einem fachlichen Fehlschlag (fehlende Zugangsdaten, SVWS-seitige Ablehnung, Netzwerkfehler)
     * bewusst 200 mit {@code success=false} statt eines 5xx - siehe {@code SchemaService#destroy}.
     */
    @POST
    @Path("/{id}/loeschen-auf-instanz")
    @Consumes(MediaType.WILDCARD)
    public SchemaDestroyResultDto destroy(@PathParam("schultraegerId") final UUID schultraegerId,
            @PathParam("schuleId") final UUID schuleId, @PathParam("id") final UUID id) {
        return service.destroy(adminSubject(), schultraegerId, schuleId, id);
    }

    private String adminSubject() {
        return securityIdentity.getPrincipal().getName();
    }
}
