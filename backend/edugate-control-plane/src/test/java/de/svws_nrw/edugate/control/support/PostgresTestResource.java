package de.svws_nrw.edugate.control.support;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Startet einen einzelnen PostgreSQL-16-Container für die Backend-Testsuite und legt darauf
 * dieselben drei Anwendungsrollen an wie {@code docker/postgres/init/01-roles.sh}. Die
 * eigentliche Schemaerstellung übernimmt Flyway über die "migration"-Datasource beim
 * Quarkus-Testboot (siehe application.properties) – identisch zum Produktivpfad.
 */
public class PostgresTestResource implements QuarkusTestResourceLifecycleManager {

    public static final String CONTROL_PASSWORD = "control-test-pw";
    public static final String OPERATOR_PASSWORD = "operator-test-pw";
    public static final String GATEWAY_PASSWORD = "gateway-test-pw";

    private static PostgreSQLContainer<?> container;

    @Override
    public Map<String, String> start() {
        container = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("edugate")
            .withUsername("postgres_admin")
            .withPassword("admin-test-pw");
        container.start();

        createApplicationRoles();

        final Map<String, String> config = new HashMap<>();
        config.put("quarkus.datasource.jdbc.url", container.getJdbcUrl());
        config.put("quarkus.datasource.username", "edugate_control");
        config.put("quarkus.datasource.password", CONTROL_PASSWORD);

        config.put("quarkus.datasource.\"operator\".jdbc.url", container.getJdbcUrl());
        config.put("quarkus.datasource.\"operator\".username", "edugate_operator");
        config.put("quarkus.datasource.\"operator\".password", OPERATOR_PASSWORD);

        config.put("quarkus.datasource.\"migration\".jdbc.url", container.getJdbcUrl());
        config.put("quarkus.datasource.\"migration\".username", container.getUsername());
        config.put("quarkus.datasource.\"migration\".password", container.getPassword());
        return config;
    }

    private void createApplicationRoles() {
        try (Connection connection = DriverManager.getConnection(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE ROLE edugate_control LOGIN PASSWORD '" + CONTROL_PASSWORD + "'");
            statement.execute("CREATE ROLE edugate_operator LOGIN PASSWORD '" + OPERATOR_PASSWORD + "'");
            statement.execute("CREATE ROLE edugate_gateway_ro LOGIN PASSWORD '" + GATEWAY_PASSWORD + "'");
            statement.execute("GRANT USAGE ON SCHEMA public TO edugate_control");
            statement.execute("GRANT USAGE ON SCHEMA public TO edugate_operator");
            statement.execute("GRANT USAGE ON SCHEMA public TO edugate_gateway_ro");
        } catch (final SQLException e) {
            throw new IllegalStateException("Konnte Test-Rollen nicht anlegen.", e);
        }
    }

    /** Rohe Admin-Verbindung für Tests, die RLS/Policies unabhängig von Quarkus prüfen müssen. */
    public static Connection openAdminConnection() throws SQLException {
        return DriverManager.getConnection(container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }

    public static String jdbcUrl() {
        return container.getJdbcUrl();
    }

    /** Rohe Verbindung als Anwendungsrolle (z. B. edugate_control), für Rechte-Tests. */
    public static Connection openConnectionAs(final String username, final String password) throws SQLException {
        return DriverManager.getConnection(container.getJdbcUrl(), username, password);
    }

    @Override
    public void stop() {
        if (container != null) {
            container.stop();
        }
    }
}
