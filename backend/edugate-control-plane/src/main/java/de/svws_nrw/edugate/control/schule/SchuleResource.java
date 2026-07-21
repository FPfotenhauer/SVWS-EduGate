package de.svws_nrw.edugate.control.schule;

import de.svws_nrw.edugate.control.schule.dto.SchuleCreateRequest;
import de.svws_nrw.edugate.control.schule.dto.SchuleDto;
import de.svws_nrw.edugate.control.schule.dto.SchuleUpdateRequest;
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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

/**
 * Schul-Verwaltung je Schulträger (Dienstleister-Admin). Schule ist eine tenant-gebundene
 * Kind-Entität (ADR-009), alle Operationen laufen daher über {@link SchuleService}/
 * {@code TenantAccess} statt über {@code OperatorAccess}, analog zu Ansprechpartner. Schulen
 * bilden die notwendige Zuordnungsstufe für Schuldatenbanken/Schemata (ADR-012).
 */
@Path("/admin/api/v1/schultraeger/{schultraegerId}/schulen")
@RolesAllowed("dienstleister-admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SchuleResource {

    @Inject
    SchuleService service;

    @Inject
    SecurityIdentity securityIdentity;

    @GET
    public List<SchuleDto> list(@PathParam("schultraegerId") final UUID schultraegerId) {
        return service.list(schultraegerId);
    }

    @GET
    @Path("/{id}")
    public SchuleDto get(@PathParam("schultraegerId") final UUID schultraegerId, @PathParam("id") final UUID id) {
        return service.get(schultraegerId, id);
    }

    @POST
    public Response create(@PathParam("schultraegerId") final UUID schultraegerId,
            @Valid final SchuleCreateRequest request) {
        final SchuleDto created = service.create(schultraegerId, request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    public SchuleDto update(@PathParam("schultraegerId") final UUID schultraegerId, @PathParam("id") final UUID id,
            @Valid final SchuleUpdateRequest request) {
        return service.update(schultraegerId, id, request);
    }

    @DELETE
    @Path("/{id}")
    public SchuleDto deactivate(@PathParam("schultraegerId") final UUID schultraegerId, @PathParam("id") final UUID id) {
        return service.deactivate(adminSubject(), schultraegerId, id);
    }

    @DELETE
    @Path("/{id}/endgueltig")
    public Response delete(@PathParam("schultraegerId") final UUID schultraegerId, @PathParam("id") final UUID id) {
        service.delete(adminSubject(), schultraegerId, id);
        return Response.noContent().build();
    }

    private String adminSubject() {
        return securityIdentity.getPrincipal().getName();
    }
}
