package de.svws_nrw.edugate.control.schuldatei;

import de.svws_nrw.edugate.control.schuldatei.dto.SchuldateiImportStatusDto;
import de.svws_nrw.edugate.control.schuldatei.dto.SchuleKatalogPageDto;
import de.svws_nrw.edugate.control.schuldatei.dto.SchultraegerKatalogPageDto;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Betreiber-Einstellung "Schuldatei" (ADR-020): Landes-Schuldatei als Referenzkatalog. Rein
 * lesende Such-/Filterlisten für Schulen und Schulträger sowie ein geschützter, auditierter
 * Refresh-Auslöser - anders als der öffentliche ({@code @PermitAll}) Refresh-Endpunkt im Projekt
 * {@code SVWS-Main-Server} (ADR-020-Nachtrag, ADR-009).
 */
@Path("/admin/api/v1/schuldatei")
@RolesAllowed("dienstleister-admin")
@Produces(MediaType.APPLICATION_JSON)
public class SchuldateiResource {

    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 200;

    @Inject
    SchuldateiService service;

    @Inject
    SecurityIdentity securityIdentity;

    @GET
    @Path("/status")
    public SchuldateiImportStatusDto status() {
        return service.letzterImport(adminSubject());
    }

    /**
     * Löst einen Schuldatei-Refresh aus (ADR-020: manueller Abruf aus der Kachel). Ersetzt die
     * Kataloge nicht komplett, sondern aktualisiert sie per Upsert - siehe
     * {@link SchuldateiService#refresh(String)}.
     */
    @POST
    @Path("/refresh")
    @Consumes(MediaType.WILDCARD)
    public SchuldateiImportStatusDto refresh() {
        return service.refresh(adminSubject());
    }

    @GET
    @Path("/schulen")
    public SchuleKatalogPageDto schulen(
            @QueryParam("page") @DefaultValue("0") final int page,
            @QueryParam("size") @DefaultValue("" + DEFAULT_PAGE_SIZE) final int size,
            @QueryParam("q") final String q,
            @QueryParam("schultraegernummer") final String schultraegernummer,
            @QueryParam("nurAktive") final Boolean nurAktive) {
        final int safePage = Math.max(page, 0);
        final int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return service.listSchulen(adminSubject(), safePage, safeSize, q, schultraegernummer, nurAktive);
    }

    @GET
    @Path("/schultraeger")
    public SchultraegerKatalogPageDto schultraeger(
            @QueryParam("page") @DefaultValue("0") final int page,
            @QueryParam("size") @DefaultValue("" + DEFAULT_PAGE_SIZE) final int size,
            @QueryParam("q") final String q,
            @QueryParam("nurAktive") final Boolean nurAktive) {
        final int safePage = Math.max(page, 0);
        final int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return service.listSchultraeger(adminSubject(), safePage, safeSize, q, nurAktive);
    }

    private String adminSubject() {
        return securityIdentity.getPrincipal().getName();
    }
}
