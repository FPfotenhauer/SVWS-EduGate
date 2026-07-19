package de.svws_nrw.edugate.control.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.svws_nrw.edugate.control.support.PostgresTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * DB-Ebenen-Nachweis der ADR-012-Geschäftsregeln aus {@code V5__schemaverwaltung.sql}, unabhängig
 * von der Service-/Resource-Schicht (zweite Verteidigungslinie, analog zu {@link
 * de.svws_nrw.edugate.control.rls.RlsGuardTest}): {@code umgebung} ist erweiterbarer Freitext
 * (kein geschlossenes CHECK mehr), {@code status}/{@code source} haben sinnvolle Startwerte,
 * {@code schema_name} ist je Instanz eindeutig, höchstens ein aktives Produktiv-Schema je Schule.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SchemaMigrationTest {

    @Test
    void umgebungAkzeptiertBeliebigeNichtLeereWerteOhneMigration() throws Exception {
        try (Connection connection = PostgresTestResource.openAdminConnection()) {
            final UUID tenantId = neuerSchultraeger(connection);
            final UUID schuleId = neueSchule(connection, tenantId, "700001");
            final UUID instanzId = neueInstanz(connection);

            // "ABNAHME" ist keiner der Startwerte (PRODUKTIV/TEST/SCHULUNG) - muss trotzdem ohne
            // Schemaänderung möglich sein (ADR-012: Umgebungen dürfen nicht hart verengt werden).
            insertSchema(connection, tenantId, schuleId, instanzId, "700001_abnahme", "ABNAHME", true);
        }
    }

    @Test
    void leereUmgebungWirdAbgelehnt() throws Exception {
        try (Connection connection = PostgresTestResource.openAdminConnection()) {
            final UUID tenantId = neuerSchultraeger(connection);
            final UUID schuleId = neueSchule(connection, tenantId, "700002");
            final UUID instanzId = neueInstanz(connection);

            assertThatThrownBy(() -> insertSchema(connection, tenantId, schuleId, instanzId, "700002_leer", "  ", true))
                .isInstanceOf(SQLException.class);
        }
    }

    @Test
    void statusHatDefaultGeplantUndLehntUnbekannteWerteAb() throws Exception {
        try (Connection connection = PostgresTestResource.openAdminConnection()) {
            final UUID tenantId = neuerSchultraeger(connection);
            final UUID schuleId = neueSchule(connection, tenantId, "700003");
            final UUID instanzId = neueInstanz(connection);

            final UUID schemaId = insertSchema(connection, tenantId, schuleId, instanzId, "700003", "PRODUKTIV", true);
            try (PreparedStatement statement = connection.prepareStatement("SELECT status, source FROM schema WHERE id = ?")) {
                statement.setObject(1, schemaId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getString("status")).isEqualTo("GEPLANT");
                    assertThat(resultSet.getString("source")).isEqualTo("MANUELL");
                }
            }

            assertThatThrownBy(() -> {
                try (PreparedStatement statement = connection.prepareStatement("UPDATE schema SET status = ? WHERE id = ?")) {
                    statement.setString(1, "UNBEKANNT");
                    statement.setObject(2, schemaId);
                    statement.executeUpdate();
                }
            }).isInstanceOf(SQLException.class);
        }
    }

    @Test
    void schemaNameIstEindeutigProInstanzAberNichtGlobal() throws Exception {
        try (Connection connection = PostgresTestResource.openAdminConnection()) {
            final UUID tenantId = neuerSchultraeger(connection);
            final UUID schuleA = neueSchule(connection, tenantId, "700004");
            final UUID schuleB = neueSchule(connection, tenantId, "700005");
            final UUID instanzA = neueInstanz(connection);
            final UUID instanzB = neueInstanz(connection);

            insertSchema(connection, tenantId, schuleA, instanzA, "geteilter-name", "TEST", true);

            assertThatThrownBy(() -> insertSchema(connection, tenantId, schuleB, instanzA, "geteilter-name", "TEST", true))
                .as("gleicher Name auf derselben Instanz ist verboten")
                .isInstanceOf(SQLException.class);

            // Gleicher Name auf einer ANDEREN Instanz ist erlaubt (Eindeutigkeit ist instanzbezogen).
            insertSchema(connection, tenantId, schuleB, instanzB, "geteilter-name", "TEST", true);
        }
    }

    @Test
    void hoechstensEinAktivesProduktivSchemaProSchuleAufDbEbene() throws Exception {
        try (Connection connection = PostgresTestResource.openAdminConnection()) {
            final UUID tenantId = neuerSchultraeger(connection);
            final UUID schuleId = neueSchule(connection, tenantId, "700006");
            final UUID instanzId = neueInstanz(connection);

            final UUID erstesSchema = insertSchema(connection, tenantId, schuleId, instanzId, "700006", "PRODUKTIV", true);

            assertThatThrownBy(() -> insertSchema(connection, tenantId, schuleId, instanzId, "700006-b", "PRODUKTIV", true))
                .as("zweites aktives Produktiv-Schema für dieselbe Schule ist verboten")
                .isInstanceOf(SQLException.class);

            // Inaktive Produktiv-Schemata (z. B. Archiv/History) bleiben uneingeschränkt möglich.
            insertSchema(connection, tenantId, schuleId, instanzId, "700006-archiv", "PRODUKTIV", false);

            try (PreparedStatement statement = connection.prepareStatement("UPDATE schema SET aktiv = false WHERE id = ?")) {
                statement.setObject(1, erstesSchema);
                statement.executeUpdate();
            }

            insertSchema(connection, tenantId, schuleId, instanzId, "700006-b", "PRODUKTIV", true);
        }
    }

    private UUID neuerSchultraeger(final Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO schultraeger (name, traegernummer) VALUES (?, ?) RETURNING id")) {
            statement.setString(1, "Migrationstest-Träger");
            statement.setString(2, "MIG-" + UUID.randomUUID());
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return (UUID) resultSet.getObject("id");
            }
        }
    }

    private UUID neueSchule(final Connection connection, final UUID tenantId, final String schulnummer) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO schule (tenant_id, schulnummer, name) VALUES (?, ?, ?) RETURNING id")) {
            statement.setObject(1, tenantId);
            statement.setString(2, schulnummer);
            statement.setString(3, "Migrationstest-Schule " + schulnummer);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return (UUID) resultSet.getObject("id");
            }
        }
    }

    private UUID neueInstanz(final Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO svws_instanz (name, base_url) VALUES (?, ?) RETURNING id")) {
            statement.setString(1, "Migrationstest-Instanz-" + UUID.randomUUID());
            statement.setString(2, "https://svws-migrationstest-" + UUID.randomUUID() + ".example.org");
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return (UUID) resultSet.getObject("id");
            }
        }
    }

    private UUID insertSchema(final Connection connection, final UUID tenantId, final UUID schuleId, final UUID instanzId,
            final String schemaName, final String umgebung, final boolean aktiv) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO schema (tenant_id, schule_id, instanz_id, schema_name, umgebung, aktiv) "
                    + "VALUES (?, ?, ?, ?, ?, ?) RETURNING id")) {
            statement.setObject(1, tenantId);
            statement.setObject(2, schuleId);
            statement.setObject(3, instanzId);
            statement.setString(4, schemaName);
            statement.setString(5, umgebung);
            statement.setBoolean(6, aktiv);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return (UUID) resultSet.getObject("id");
            }
        }
    }
}
