package de.svws_nrw.edugate.control.tenant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.UUID;

/**
 * Setzt den Mandanten-Kontext für Tenant-gebundene Zugriffe über die {@code default}-Datasource
 * (Rolle {@code edugate_control}) mittels {@code SET LOCAL edugate.tenant_id}, gegen den die
 * PostgreSQL-RLS-Policies aus ADR-002/ADR-008 prüfen.
 *
 * <p>{@code SET LOCAL} gilt nur für die laufende Transaktion; Methoden dieser Klasse müssen
 * daher innerhalb einer laufenden {@code @Transactional}-Grenze aufgerufen werden. In diesem
 * Auftrag wird {@code TenantContext} ausschließlich durch Tests exerziert (Cross-Tenant-
 * Negativtest); die Schulträger-Endpunkte laufen vollständig über {@code OperatorAccess}, da
 * Schulträger-CRUD per Definition mandantenübergreifend ist (ADR-009). Der Gateway-Service wird
 * diese Komponente später für die Mandanten-Auflösung mitnutzen (ADR-004).
 *
 * <p>{@code SET LOCAL} unterstützt keine gebundenen Parameter; da {@code tenantId} ein
 * validierter {@link UUID}-Typ ist (kanonisches Format, keine Nutzereingabe als Rohstring),
 * ist die Verkettung in das SQL-Statement ungefährlich.
 */
@ApplicationScoped
public class TenantContext {

    @Inject
    EntityManager entityManager;

    public void setTenant(final UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId darf nicht null sein");
        entityManager.createNativeQuery("SET LOCAL edugate.tenant_id = '" + tenantId + "'").executeUpdate();
    }
}
