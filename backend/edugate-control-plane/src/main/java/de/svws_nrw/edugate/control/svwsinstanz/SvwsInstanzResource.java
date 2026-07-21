package de.svws_nrw.edugate.control.svwsinstanz;

import de.svws_nrw.edugate.control.svwsinstanz.dto.SchemaFundZuordnungRequest;
import de.svws_nrw.edugate.control.svwsinstanz.dto.SvwsInstanzCreateRequest;
import de.svws_nrw.edugate.control.svwsinstanz.dto.SvwsInstanzCredentialsRequest;
import de.svws_nrw.edugate.control.svwsinstanz.dto.SvwsInstanzDto;
import de.svws_nrw.edugate.control.svwsinstanz.dto.SvwsInstanzPageDto;
import de.svws_nrw.edugate.control.svwsinstanz.dto.SvwsInstanzUpdateRequest;
import de.svws_nrw.edugate.control.svwsinstanz.dto.SvwsSchemaFundDto;
import de.svws_nrw.edugate.control.svwsinstanz.dto.SvwsSchemaSyncResultDto;
import de.svws_nrw.edugate.control.svwsinstanz.dto.SvwsSchulInfoResultDto;
import de.svws_nrw.edugate.core.domain.InstanzStatus;
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
import java.util.List;
import java.util.UUID;

/**
 * SVWS-Instanz-Verwaltung (Dienstleister-Admin). Eine SVWS-Instanz ist eine
 * mandantenübergreifende Betriebsressource (ADR-011); alle Operationen laufen daher über
 * {@code OperatorAccess} (ADR-009), analog zur Mandanten-Wurzel schultraeger.
 */
@Path("/admin/api/v1/svws-instanzen")
@RolesAllowed("dienstleister-admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SvwsInstanzResource {

    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 100;

    @Inject
    SvwsInstanzService service;

    @Inject
    SvwsSchemaFundService schemaFundService;

    @Inject
    SecurityIdentity securityIdentity;

    @GET
    public SvwsInstanzPageDto list(
            @QueryParam("page") @DefaultValue("0") final int page,
            @QueryParam("size") @DefaultValue("" + DEFAULT_PAGE_SIZE) final int size,
            @QueryParam("q") final String q,
            @QueryParam("status") final InstanzStatus status) {
        final int safePage = Math.max(page, 0);
        final int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return service.list(adminSubject(), safePage, safeSize, q, status);
    }

    @POST
    public Response create(@Valid final SvwsInstanzCreateRequest request) {
        final SvwsInstanzDto created = service.create(adminSubject(), request);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @GET
    @Path("/{id}")
    public SvwsInstanzDto get(@PathParam("id") final UUID id) {
        return service.get(adminSubject(), id);
    }

    @PUT
    @Path("/{id}")
    public SvwsInstanzDto update(@PathParam("id") final UUID id, @Valid final SvwsInstanzUpdateRequest request) {
        return service.update(adminSubject(), id, request);
    }

    @PUT
    @Path("/{id}/credentials")
    public Response setCredentials(@PathParam("id") final UUID id, @Valid final SvwsInstanzCredentialsRequest request) {
        service.setCredentials(adminSubject(), id, request);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{id}")
    public Response deactivate(@PathParam("id") final UUID id) {
        service.deactivate(adminSubject(), id);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/connection-test")
    @Consumes(MediaType.WILDCARD)
    public SvwsInstanzDto testConnection(@PathParam("id") final UUID id) {
        return service.testConnection(adminSubject(), id);
    }

    /**
     * Read-only Sync mit der SVWS-Privileged-API (ADR-014 Stufe 1): liest die auf der Instanz
     * technisch vorhandenen SVWS-Schemata und ersetzt die gespeicherten Funde
     * ({@link #schemaFunde(UUID)}) durch das aktuelle Ergebnis. Verändert ausschließlich
     * EduGate-interne Sync-Daten, keine SVWS-Daten (keine "gefährliche Operation" i. S. v.
     * ADR-013).
     */
    @POST
    @Path("/{id}/schema-sync")
    @Consumes(MediaType.WILDCARD)
    public SvwsSchemaSyncResultDto syncSchemas(@PathParam("id") final UUID id) {
        return schemaFundService.sync(adminSubject(), id);
    }

    /**
     * Liefert die zuletzt gespeicherten Sync-Funde der Instanz (ADR-012: "Nutze vorhandene
     * Sync-/Fund-Daten ... statt direkt im Frontend gegen SVWS zu sprechen") - liest ausschließlich
     * {@code svws_schema_fund}, ruft nicht selbst die SVWS-Instanz auf.
     */
    @GET
    @Path("/{id}/schema-funde")
    public List<SvwsSchemaFundDto> schemaFunde(@PathParam("id") final UUID id) {
        return schemaFundService.list(adminSubject(), id);
    }

    /**
     * Ordnet einen unzugeordneten SVWS-Schema-Fund kontrolliert einer Schule zu (ADR-014
     * Schritt 2): erzeugt oder aktualisiert den passenden EduGate-{@code schema}-Datensatz.
     * Nach Erfolg klassifiziert {@link #schemaFunde(UUID)} den Fund als {@code BEKANNT}.
     */
    @POST
    @Path("/{id}/schema-funde/{fundId}/zuordnung")
    public SvwsSchemaFundDto schemaFundZuordnen(@PathParam("id") final UUID id, @PathParam("fundId") final UUID fundId,
            @Valid final SchemaFundZuordnungRequest request) {
        return schemaFundService.assign(adminSubject(), id, fundId, request);
    }

    /**
     * Liefert optional die im SVWS-Schema hinterlegten Schulinformationen als Orientierung beim
     * Zuordnen (ADR-014 Schritt 2). Liefert immer 200: Ein SVWS-seitiger Fehlschlag (keine Rechte,
     * keine Informationen, Netzwerkfehler) ist fachlich normal und darf die manuelle Zuordnung
     * nicht blockieren - {@link SvwsSchulInfoResultDto#success()} zeigt das Ergebnis an.
     */
    @GET
    @Path("/{id}/schema-funde/{fundId}/schulinfo")
    public SvwsSchulInfoResultDto schemaFundSchulInfo(@PathParam("id") final UUID id,
            @PathParam("fundId") final UUID fundId) {
        return schemaFundService.schulInfo(adminSubject(), id, fundId);
    }

    private String adminSubject() {
        return securityIdentity.getPrincipal().getName();
    }
}
