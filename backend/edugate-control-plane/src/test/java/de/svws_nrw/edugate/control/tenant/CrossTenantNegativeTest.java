package de.svws_nrw.edugate.control.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import de.svws_nrw.edugate.control.support.PostgresTestResource;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Cross-Tenant-Negativtest gemäß ADR-002/ADR-008/ADR-009: Als {@code edugate_control} mit
 * Tenant-Kontext A liefert ein {@code SELECT} auf Zeilen von Tenant B null Zeilen – sowohl für
 * {@code schule} (Tenant-Policy über {@code tenant_id}) als auch für {@code schultraeger}
 * (Wurzel-Policy über {@code id}, ADR-008).
 *
 * <p>Testdaten werden direkt über die Superuser-Verbindung eingefügt (umgeht RLS bewusst, um
 * bekannte Ausgangsdaten für zwei Mandanten zu erzeugen) – exerziert dann {@link TenantContext}
 * genau wie es später der Gateway-Service tun wird.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class CrossTenantNegativeTest {

    private static UUID tenantA;
    private static UUID tenantB;
    private static UUID schuleUnterTenantB;

    @Inject
    TenantContext tenantContext;

    @Inject
    EntityManager entityManager;

    @BeforeAll
    static void seedTestdaten() throws Exception {
        tenantA = UUID.randomUUID();
        tenantB = UUID.randomUUID();
        schuleUnterTenantB = UUID.randomUUID();

        try (Connection connection = PostgresTestResource.openAdminConnection()) {
            try (PreparedStatement schultraeger = connection.prepareStatement(
                    "INSERT INTO schultraeger (id, name, traegernummer) VALUES (?, ?, ?)")) {
                schultraeger.setObject(1, tenantA);
                schultraeger.setString(2, "Schulträger A (Cross-Tenant-Test)");
                schultraeger.setString(3, "XT-A-" + tenantA);
                schultraeger.executeUpdate();

                schultraeger.setObject(1, tenantB);
                schultraeger.setString(2, "Schulträger B (Cross-Tenant-Test)");
                schultraeger.setString(3, "XT-B-" + tenantB);
                schultraeger.executeUpdate();
            }

            try (PreparedStatement schule = connection.prepareStatement(
                    "INSERT INTO schule (id, tenant_id, schulnummer, name) VALUES (?, ?, ?, ?)")) {
                schule.setObject(1, schuleUnterTenantB);
                schule.setObject(2, tenantB);
                schule.setString(3, "999999");
                schule.setString(4, "Schule unter Tenant B");
                schule.executeUpdate();
            }
        }
    }

    @Test
    @TestTransaction
    void crossTenantSelectAufSchuleLiefertKeineZeilen() {
        tenantContext.setTenant(tenantA);

        final List<?> result = entityManager
            .createNativeQuery("SELECT id FROM schule WHERE tenant_id = :tenantId")
            .setParameter("tenantId", tenantB)
            .getResultList();

        assertThat(result).isEmpty();
    }

    @Test
    @TestTransaction
    void tenantASiehtWeiterhinDieEigeneSchule() {
        tenantContext.setTenant(tenantB);

        final List<?> result = entityManager
            .createNativeQuery("SELECT id FROM schule WHERE id = :id")
            .setParameter("id", schuleUnterTenantB)
            .getResultList();

        assertThat(result).hasSize(1);
    }

    @Test
    @TestTransaction
    void crossTenantSelectAufSchultraegerLiefertKeineZeilen() {
        tenantContext.setTenant(tenantA);

        final List<?> result = entityManager
            .createNativeQuery("SELECT id FROM schultraeger WHERE id = :id")
            .setParameter("id", tenantB)
            .getResultList();

        assertThat(result).isEmpty();
    }
}
