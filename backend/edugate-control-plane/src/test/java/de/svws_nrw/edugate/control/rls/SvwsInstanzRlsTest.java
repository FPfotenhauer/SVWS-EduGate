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
 * RLS-/Architekturtest gemäß ADR-011: {@code svws_instanz} ist bewusst eine
 * mandantenübergreifende Betriebsressource und darf nicht versehentlich (wieder) als
 * tenant-gebundene Tabelle modelliert werden. Ergänzt die generischen Prüfregeln aus
 * {@link RlsGuardTest} (ADR-008) um die dritte, in ADR-011 eingeführte Regel.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SvwsInstanzRlsTest {

    private static final String TABLE = "svws_instanz";

    @Test
    void svwsInstanzHatKeineTenantIdSpalte() throws Exception {
        try (Connection connection = PostgresTestResource.openAdminConnection()) {
            assertThat(hasColumn(connection, "tenant_id"))
                .as("svws_instanz ist laut ADR-011 keine Mandanten-Wurzel und kein Mandanten-Kind")
                .isFalse();
        }
    }

    @Test
    void svwsInstanzHatRlsEnableUndForce() throws Exception {
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
    void svwsInstanzHatKeineTenantPolicyMehr() throws Exception {
        try (Connection connection = PostgresTestResource.openAdminConnection()) {
            final boolean hasTenantPolicy = policies(connection).stream()
                .anyMatch(qual -> qual != null && qual.contains("tenant_id") && qual.contains("current_setting"));

            assertThat(hasTenantPolicy)
                .as("svws_instanz darf keine Policy mehr haben, die tenant_id gegen current_setting prüft")
                .isFalse();
        }
    }

    @Test
    void svwsInstanzHatOperatorUndGatewayReadPolicy() throws Exception {
        try (Connection connection = PostgresTestResource.openAdminConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT roles::text AS roles, cmd FROM pg_policies WHERE tablename = ?")) {
                statement.setString(1, TABLE);
                try (ResultSet resultSet = statement.executeQuery()) {
                    boolean hasOperatorAll = false;
                    boolean hasGatewayRead = false;
                    while (resultSet.next()) {
                        final String roles = resultSet.getString("roles");
                        final String cmd = resultSet.getString("cmd");
                        if (roles != null && roles.contains("edugate_operator") && "ALL".equals(cmd)) {
                            hasOperatorAll = true;
                        }
                        if (roles != null && roles.contains("edugate_gateway_ro") && "SELECT".equals(cmd)) {
                            hasGatewayRead = true;
                        }
                    }
                    assertThat(hasOperatorAll)
                        .as("svws_instanz braucht eine FOR ALL-Policy für edugate_operator (ADR-009)")
                        .isTrue();
                    assertThat(hasGatewayRead)
                        .as("svws_instanz braucht eine FOR SELECT-Policy für edugate_gateway_ro (ADR-011)")
                        .isTrue();
                }
            }
        }
    }

    private List<String> policies(final Connection connection) throws Exception {
        final List<String> result = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT qual FROM pg_policies WHERE tablename = ?")) {
            statement.setString(1, TABLE);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(resultSet.getString("qual"));
                }
            }
        }
        return result;
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
