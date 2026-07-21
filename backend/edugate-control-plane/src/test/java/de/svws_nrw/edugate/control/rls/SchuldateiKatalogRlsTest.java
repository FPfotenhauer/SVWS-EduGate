package de.svws_nrw.edugate.control.rls;

import static org.assertj.core.api.Assertions.assertThat;

import de.svws_nrw.edugate.control.support.PostgresTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * RLS-/Architekturtest für {@code schultraeger_katalog}, {@code schule_katalog} und
 * {@code schuldatei_import} (ADR-020-Nachtrag, ADR-011-Muster): mandantenübergreifende
 * Betriebsressourcen wie {@code schema_umgebung}/{@code svws_schema_fund} - keine
 * {@code tenant_id}-Spalte, RLS ENABLE+FORCE, nur eine Operator-Policy, keine Policy für
 * {@code edugate_control} oder {@code edugate_gateway_ro}.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SchuldateiKatalogRlsTest {

    private static final List<String> TABLES = List.of("schultraeger_katalog", "schule_katalog", "schuldatei_import");

    @Test
    void keineTabelleHatEineTenantIdSpalte() throws Exception {
        try (Connection connection = PostgresTestResource.openAdminConnection()) {
            for (final String table : TABLES) {
                assertThat(hasColumn(connection, table, "tenant_id"))
                    .as("%s ist eine mandantenübergreifende Betriebsressource (ADR-020/ADR-011)", table)
                    .isFalse();
            }
        }
    }

    @Test
    void alleTabellenHabenRlsEnableUndForce() throws Exception {
        try (Connection connection = PostgresTestResource.openAdminConnection()) {
            for (final String table : TABLES) {
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
        }
    }

    @Test
    void alleTabellenHabenNurEineOperatorAllPolicy() throws Exception {
        try (Connection connection = PostgresTestResource.openAdminConnection()) {
            for (final String table : TABLES) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT roles::text AS roles, cmd FROM pg_policies WHERE tablename = ?")) {
                    statement.setString(1, table);
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
                            .as("%s braucht eine FOR ALL-Policy für edugate_operator (ADR-009/011)", table)
                            .isTrue();
                        assertThat(anzahlPolicies)
                            .as("keine weiteren Policies auf %s - insbesondere keine für edugate_control", table)
                            .isEqualTo(1);
                    }
                }
            }
        }
    }

    @Test
    void schuleKatalogHatEindeutigkeitJeBundeslandUndSchulnummer() throws Exception {
        try (Connection connection = PostgresTestResource.openAdminConnection()) {
            assertThat(hasUniqueConstraint(connection, "schule_katalog", "schule_katalog_bundesland_schulnummer_unique"))
                .isTrue();
        }
    }

    @Test
    void schultraegerKatalogHatEindeutigkeitJeBundeslandUndTraegernummer() throws Exception {
        try (Connection connection = PostgresTestResource.openAdminConnection()) {
            assertThat(hasUniqueConstraint(connection, "schultraeger_katalog",
                "schultraeger_katalog_bundesland_traegernummer_unique")).isTrue();
        }
    }

    private boolean hasUniqueConstraint(final Connection connection, final String table, final String constraintName)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM pg_constraint WHERE conname = ? AND conrelid = ?::regclass")) {
            statement.setString(1, constraintName);
            statement.setString(2, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
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
}
