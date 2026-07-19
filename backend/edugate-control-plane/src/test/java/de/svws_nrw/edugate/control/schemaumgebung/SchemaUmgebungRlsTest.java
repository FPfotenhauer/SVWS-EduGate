package de.svws_nrw.edugate.control.schemaumgebung;

import static org.assertj.core.api.Assertions.assertThat;

import de.svws_nrw.edugate.control.support.PostgresTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

/**
 * RLS-/Architekturtest für {@code schema_umgebung} (ADR-012/ADR-011-Muster): mandantenübergreifende
 * Betriebsressource wie {@code svws_instanz} - keine {@code tenant_id}-Spalte, RLS ENABLE+FORCE,
 * nur eine Operator-Policy, keine Policy für {@code edugate_control}.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SchemaUmgebungRlsTest {

    private static final String TABLE = "schema_umgebung";

    @Test
    void schemaUmgebungHatKeineTenantIdSpalte() throws Exception {
        try (Connection connection = PostgresTestResource.openAdminConnection()) {
            assertThat(hasColumn(connection, "tenant_id"))
                .as("schema_umgebung ist eine mandantenübergreifende Betreiber-Konvention, kein Mandanten-Objekt")
                .isFalse();
        }
    }

    @Test
    void schemaUmgebungHatRlsEnableUndForce() throws Exception {
        try (Connection connection = PostgresTestResource.openAdminConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT relrowsecurity, relforcerowsecurity FROM pg_class WHERE relname = ? AND relkind = 'r'")) {
                statement.setString(1, TABLE);
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).as("Tabelle %s existiert", TABLE).isTrue();
                    assertThat(resultSet.getBoolean("relrowsecurity")).as("RLS ENABLE auf %s", TABLE).isTrue();
                    assertThat(resultSet.getBoolean("relforcerowsecurity")).as("RLS FORCE auf %s", TABLE).isTrue();
                }
            }
        }
    }

    @Test
    void schemaUmgebungHatNurEineOperatorAllPolicyUndKeineWeitere() throws Exception {
        try (Connection connection = PostgresTestResource.openAdminConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT roles::text AS roles, cmd FROM pg_policies WHERE tablename = ?")) {
                statement.setString(1, TABLE);
                try (ResultSet resultSet = statement.executeQuery()) {
                    int anzahlPolicies = 0;
                    boolean hasOperatorAll = false;
                    while (resultSet.next()) {
                        anzahlPolicies++;
                        final String roles = resultSet.getString("roles");
                        final String cmd = resultSet.getString("cmd");
                        if (roles != null && roles.contains("edugate_operator") && "ALL".equals(cmd)) {
                            hasOperatorAll = true;
                        }
                    }
                    assertThat(hasOperatorAll)
                        .as("schema_umgebung braucht eine FOR ALL-Policy für edugate_operator (ADR-009/011)")
                        .isTrue();
                    assertThat(anzahlPolicies)
                        .as("keine weiteren Policies - insbesondere keine für edugate_control (ADR-011-Muster)")
                        .isEqualTo(1);
                }
            }
        }
    }

    private boolean hasColumn(final Connection connection, final String column) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' "
                    + "AND table_name = ? AND column_name = ?")) {
            statement.setString(1, TABLE);
            statement.setString(2, column);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }
}
