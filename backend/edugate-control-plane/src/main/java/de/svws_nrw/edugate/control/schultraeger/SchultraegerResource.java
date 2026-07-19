package de.svws_nrw.edugate.control.schultraeger;

import de.svws_nrw.edugate.control.schultraeger.dto.SchultraegerCreateRequest;
import de.svws_nrw.edugate.control.schultraeger.dto.SchultraegerDto;
import de.svws_nrw.edugate.control.schultraeger.dto.SchultraegerPageDto;
import de.svws_nrw.edugate.control.schultraeger.dto.SchultraegerUpdateRequest;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.UUID;

/**
 * Schulträger-Verwaltung (Dienstleister-Admin). Alle Operationen sind per Definition
 * mandantenübergreifend und laufen daher über {@code OperatorAccess} (ADR-009).
 */
@Path("/admin/api/v1/schultraeger")
@RolesAllowed("dienstleister-admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SchultraegerResource {

    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 100;

    @Inject
    SchultraegerService service;

    @Inject
    SecurityIdentity securityIdentity;

    @GET
    public SchultraegerPageDto list(
            @QueryParam("page") @DefaultValue("0") final int page,
            @QueryParam("size") @DefaultValue("" + DEFAULT_PAGE_SIZE) final int size,
            @QueryParam("q") final String q) {
        final int safePage = Math.max(page, 0);
        final int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return service.list(adminSubject(), safePage, safeSize, q);
    }

    @POST
    public Response create(@Valid final SchultraegerCreateRequest request) {
        final SchultraegerDto created = service.create(adminSubject(), request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @GET
    @Path("/{id}")
    public SchultraegerDto get(@PathParam("id") final UUID id) {
        return service.get(adminSubject(), id);
    }

    @PUT
    @Path("/{id}")
    public SchultraegerDto update(@PathParam("id") final UUID id, @Valid final SchultraegerUpdateRequest request) {
        return service.update(adminSubject(), id, request);
    }

    @DELETE
    @Path("/{id}")
    public Response deactivate(@PathParam("id") final UUID id) {
        service.deactivate(adminSubject(), id);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/reaktivieren")
    @Consumes(MediaType.WILDCARD)
    public SchultraegerDto reactivate(@PathParam("id") final UUID id) {
        return service.reactivate(adminSubject(), id);
    }

    private String adminSubject() {
        return securityIdentity.getPrincipal().getName();
    }
}
