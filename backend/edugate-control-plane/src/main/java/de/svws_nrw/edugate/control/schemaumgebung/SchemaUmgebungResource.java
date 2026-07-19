package de.svws_nrw.edugate.control.schemaumgebung;

import de.svws_nrw.edugate.control.schemaumgebung.dto.SchemaUmgebungCreateRequest;
import de.svws_nrw.edugate.control.schemaumgebung.dto.SchemaUmgebungDto;
import de.svws_nrw.edugate.control.schemaumgebung.dto.SchemaUmgebungUpdateRequest;
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
 * Verwaltung der Schema-Umgebungen (Dienstleister-Admin, Betreiber-Einstellungen, ADR-012).
 * {@code schema_umgebung} ist eine mandantenübergreifende Betriebsressource (ADR-011-Muster);
 * alle Operationen laufen daher über {@code OperatorAccess}, analog zu SVWS-Instanzen. Dieser
 * Endpunkt dient sowohl der Verwaltungsseite "Umgebungen verwalten" als auch als Quelle für die
 * Umgebungs-Auswahl im Schema-Anlegen-Formular.
 */
@Path("/admin/api/v1/schema-umgebungen")
@RolesAllowed("dienstleister-admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SchemaUmgebungResource {

    @Inject
    SchemaUmgebungService service;

    @Inject
    SecurityIdentity securityIdentity;

    @GET
    public List<SchemaUmgebungDto> list() {
        return service.list(adminSubject());
    }

    @POST
    public Response create(@Valid final SchemaUmgebungCreateRequest request) {
        final SchemaUmgebungDto created = service.create(adminSubject(), request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @PUT
    @Path("/{id}")
    public SchemaUmgebungDto update(@PathParam("id") final UUID id, @Valid final SchemaUmgebungUpdateRequest request) {
        return service.update(adminSubject(), id, request);
    }

    @DELETE
    @Path("/{id}")
    public SchemaUmgebungDto deactivate(@PathParam("id") final UUID id) {
        return service.deactivate(adminSubject(), id);
    }

    private String adminSubject() {
        return securityIdentity.getPrincipal().getName();
    }
}
