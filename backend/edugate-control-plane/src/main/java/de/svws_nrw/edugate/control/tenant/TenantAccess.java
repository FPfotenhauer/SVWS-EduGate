package de.svws_nrw.edugate.control.tenant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.hibernate.Session;

/**
 * Zugriffspfad für tenant-gebundene Kind-Entitäten unterhalb der Mandanten-Wurzel (ADR-009,
 * Beispiel dort: "Schulen"). Setzt den Mandanten-Kontext über {@link TenantContext} und führt die
 * Operation danach in derselben Transaktion aus - RLS (ADR-002/ADR-008) filtert automatisch auf
 * den gesetzten Mandanten, zusätzlich zur expliziten {@code tenant_id}-Prüfung in den Queries der
 * jeweiligen Fachoperationen.
 *
 * <p>{@link Session#doReturningWork} ist der offiziell dokumentierte Hibernate-Weg, innerhalb der
 * aktuellen JTA-Transaktion (und damit auf derselben Connection, auf der {@link TenantContext}
 * gerade {@code SET LOCAL} ausgeführt hat) rohes JDBC auszuführen - das erlaubt denselben
 * PreparedStatement/ResultSet-Stil wie im {@code OperatorAccess}-Pfad, ohne auf JPA-Entities
 * umzusteigen.
 *
 * <p>Im Unterschied zu {@code OperatorAccess} entsteht hier kein {@code audit_admin}-Eintrag:
 * Das Admin-Audit ist laut ADR-009 an den {@code OperatorAccess}-Pfad gebunden, nicht an
 * tenant-gebundene Fachoperationen.
 */
@ApplicationScoped
public class TenantAccess {

    @Inject
    TenantContext tenantContext;

    @Inject
    EntityManager entityManager;

    @Transactional
    public <T> T execute(final UUID tenantId, final TenantOperation<T> operation) {
        tenantContext.setTenant(tenantId);
        return entityManager.unwrap(Session.class).doReturningWork(operation::execute);
    }
}
