package de.svws_nrw.edugate.control.rls;

import static org.assertj.core.api.Assertions.assertThat;

import de.svws_nrw.edugate.control.support.PostgresTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * RLS-Wächter-Test gemäß ADR-008: verhindert, dass eine neue mandantenbezogene Tabelle ohne
 * Row-Level Security die Migration übersteht (ARCHITECTURE.md Kap. 11, Risiko "RLS wird bei
 * neuen Tabellen vergessen").
 *
 * <p>Zwei mechanische Prüfregeln aus ADR-008:
 * <ol>
 *   <li>Jede Tabelle mit einer Spalte {@code tenant_id} hat RLS ENABLE+FORCE und mindestens
 *       eine Policy, die {@code tenant_id} gegen {@code current_setting('edugate.tenant_id')}
 *       prüft. Ausgenommen: {@code audit_admin} (bewusst ohne RLS, siehe ADR-008-Präzisierung
 *       und Kommentar in V1__initial_schema.sql – Admin-Audit ist mandantenübergreifend
 *       einsehbar und enthält absichtlich NULL-tenant_id-Zeilen).</li>
 *   <li>Die Tabelle {@code schultraeger} hat RLS ENABLE+FORCE und mindestens eine Policy, die
 *       {@code id} gegen {@code current_setting('edugate.tenant_id')} prüft.</li>
 * </ol>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class RlsGuardTest {

    private static final String AUDIT_TABLE_EXCEPTION = "audit_admin";

    @Test
    void jedeTabelleMitTenantIdSpalteHatRlsEnableForceUndTenantPolicy() throws Exception {
        try (Connection connection = PostgresTestResource.openAdminConnection()) {
            final List<String> tenantTables = tablesWithColumn(connection, "tenant_id", AUDIT_TABLE_EXCEPTION);

            assertThat(tenantTables).as("mindestens die drei Fachtabellen mit tenant_id")
                .contains("schule", "svws_instanz", "schema");

            for (final String table : tenantTables) {
                assertRlsEnabledAndForced(connection, table);
                assertHasPolicyReferencing(connection, table, "tenant_id", "current_setting");
            }
        }
    }

    @Test
    void schultraegerHatRlsEnableForceUndWurzelPolicyAufId() throws Exception {
        try (Connection connection = PostgresTestResource.openAdminConnection()) {
            assertRlsEnabledAndForced(connection, "schultraeger");
            assertHasPolicyReferencing(connection, "schultraeger", "id", "current_setting");
        }
    }

    @Test
    void auditAdminIstBewusstVonDerTenantIdRlsRegelAusgenommen() throws Exception {
        try (Connection connection = PostgresTestResource.openAdminConnection()) {
            assertThat(hasColumn(connection, AUDIT_TABLE_EXCEPTION, "tenant_id"))
                .as("audit_admin hat laut ADR-009 eine (nullable) tenant_id-Spalte")
                .isTrue();
            // Bewusst keine RLS-Prüfung hier - siehe Klassenkommentar und ADR-008-Präzisierung.
        }
    }

    private void assertRlsEnabledAndForced(final Connection connection, final String table) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT relrowsecurity, relforcerowsecurity FROM pg_class WHERE relname = ? AND relkind = 'r'")) {
            statement.setString(1, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).as("Tabelle %s existiert", table).isTrue();
                assertThat(resultSet.getBoolean("relrowsecurity")).as("RLS ENABLE auf %s", table).isTrue();
                assertThat(resultSet.getBoolean("relforcerowsecurity")).as("RLS FORCE auf %s", table).isTrue();
            }
        }
    }

    private void assertHasPolicyReferencing(final Connection connection, final String table, final String column,
            final String function) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT qual FROM pg_policies WHERE tablename = ?")) {
            statement.setString(1, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                boolean found = false;
                while (resultSet.next()) {
                    final String qual = resultSet.getString("qual");
                    if (qual != null && qual.contains(column) && qual.contains(function)) {
                        found = true;
                        break;
                    }
                }
                assertThat(found)
                    .as("Tabelle %s hat eine Policy, die %s gegen %s prüft", table, column, function)
                    .isTrue();
            }
        }
    }

    private boolean hasColumn(final Connection connection, final String table, final String column) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' "
                    + "AND table_name = ? AND column_name = ?")) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private List<String> tablesWithColumn(final Connection connection, final String column, final String exceptTable)
            throws Exception {
        final List<String> tables = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT table_name FROM information_schema.columns "
                    + "WHERE table_schema = 'public' AND column_name = ? AND table_name <> ?")) {
            statement.setString(1, column);
            statement.setString(2, exceptTable);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    tables.add(resultSet.getString("table_name"));
                }
            }
        }
        return tables;
    }
}
