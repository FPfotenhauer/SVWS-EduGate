package de.svws_nrw.edugate.control.ansprechpartner;

import de.svws_nrw.edugate.control.ansprechpartner.dto.AnsprechpartnerCreateRequest;
import de.svws_nrw.edugate.control.ansprechpartner.dto.AnsprechpartnerDto;
import de.svws_nrw.edugate.control.ansprechpartner.dto.AnsprechpartnerUpdateRequest;
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
 * Ansprechpartner-Verwaltung je Schulträger (Dienstleister-Admin). Ansprechpartner ist eine
 * tenant-gebundene Kind-Entität (ADR-009), alle Operationen laufen daher über
 * {@link AnsprechpartnerService}/{@code TenantAccess} statt über {@code OperatorAccess}.
 */
@Path("/admin/api/v1/schultraeger/{schultraegerId}/ansprechpartner")
@RolesAllowed("dienstleister-admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AnsprechpartnerResource {

    @Inject
    AnsprechpartnerService service;

    @GET
    public List<AnsprechpartnerDto> list(@PathParam("schultraegerId") final UUID schultraegerId) {
        return service.list(schultraegerId);
    }

    @POST
    public Response create(@PathParam("schultraegerId") final UUID schultraegerId,
            @Valid final AnsprechpartnerCreateRequest request) {
        final AnsprechpartnerDto created = service.create(schultraegerId, request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    public AnsprechpartnerDto update(@PathParam("schultraegerId") final UUID schultraegerId,
            @PathParam("id") final UUID id, @Valid final AnsprechpartnerUpdateRequest request) {
        return service.update(schultraegerId, id, request);
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("schultraegerId") final UUID schultraegerId, @PathParam("id") final UUID id) {
        service.delete(schultraegerId, id);
        return Response.noContent().build();
    }
}
