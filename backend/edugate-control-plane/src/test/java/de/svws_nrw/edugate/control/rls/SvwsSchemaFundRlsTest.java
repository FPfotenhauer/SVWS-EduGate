package de.svws_nrw.edugate.control.rls;

import static org.assertj.core.api.Assertions.assertThat;

import de.svws_nrw.edugate.control.support.PostgresTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;

/**
 * RLS-/Architekturtest für {@code svws_schema_fund} (ADR-011-Muster, wie bereits für
 * {@code schema_umgebung} etabliert): mandantenübergreifende Betriebsressource wie
 * {@code svws_instanz} - keine {@code tenant_id}-Spalte, RLS ENABLE+FORCE, nur eine
 * Operator-Policy, keine Policy für {@code edugate_control} oder {@code edugate_gateway_ro}
 * (Sync-Funde sind reine Admin-Review-Daten, keine Gateway-Routing-Grundlage, ADR-014).
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SvwsSchemaFundRlsTest {

    private static final String TABLE = "svws_schema_fund";

    @Test
    void svwsSchemaFundHatKeineTenantIdSpalte() throws Exception {
        try (Connection connection = PostgresTestResource.openAdminConnection()) {
            assertThat(hasColumn(connection, "tenant_id"))
                .as("svws_schema_fund gehört zur Instanz, nicht zu einem Mandanten (ADR-011/ADR-014)")
                .isFalse();
        }
    }

    @Test
    void svwsSchemaFundHatRlsEnableUndForce() throws Exception {
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
    void svwsSchemaFundHatNurEineOperatorAllPolicyUndKeineWeitere() throws Exception {
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
                        .as("svws_schema_fund braucht eine FOR ALL-Policy für edugate_operator (ADR-009/011)")
                        .isTrue();
                    assertThat(anzahlPolicies)
                        .as("keine weiteren Policies - insbesondere keine für edugate_control oder edugate_gateway_ro")
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
