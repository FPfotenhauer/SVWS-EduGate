package de.svws_nrw.edugate.control.operator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.svws_nrw.edugate.control.support.PostgresTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Audit-Invarianten gemäß ADR-009:
 * <ol>
 *   <li>Jede {@link OperatorAccess}-Operation erzeugt genau einen {@code audit_admin}-Eintrag.</li>
 *   <li>Im provozierten ERROR-Fall bleibt der Audit-Eintrag trotz Rollback der Fachtransaktion
 *       erhalten, die fachliche Änderung dagegen nicht.</li>
 *   <li>{@code audit_admin} verweigert {@code UPDATE}/{@code DELETE} für Anwendungsrollen.</li>
 * </ol>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class AuditInvariantsTest {

    @Inject
    OperatorAccess operatorAccess;

    @Test
    void erfolgreicheOperationErzeugtGenauEinenSuccessAuditEintrag() throws Exception {
        final String traegernummer = "AUDIT-OK-" + UUID.randomUUID();
        final String adminSubject = "audit-success-" + UUID.randomUUID();

        final UUID entityId = operatorAccess.execute(adminSubject, AuditAction.SCHULTRAEGER_CREATE, "schultraeger",
            connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO schultraeger (name, traegernummer) VALUES (?, ?) RETURNING id")) {
                    statement.setString(1, "Audit-Test");
                    statement.setString(2, traegernummer);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        resultSet.next();
                        final UUID id = (UUID) resultSet.getObject("id");
                        return OperatorOutcome.of(id, id);
                    }
                }
            });

        final List<String> outcomes = auditOutcomesFor(adminSubject, AuditAction.SCHULTRAEGER_CREATE);
        assertThat(outcomes).as("genau ein Audit-Eintrag für die Operation").containsExactly("SUCCESS");
        assertThat(entityId).isNotNull();
    }

    @Test
    void provozierterFehlerRolltFachlicheAenderungZurueckUndBehaeltAuditError() throws Exception {
        final String traegernummer = "AUDIT-ERR-" + UUID.randomUUID();
        final String adminSubject = "audit-error-" + UUID.randomUUID();

        assertThatThrownBy(() -> operatorAccess.execute(adminSubject, AuditAction.SCHULTRAEGER_CREATE, "schultraeger",
            connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO schultraeger (name, traegernummer) VALUES (?, ?)")) {
                    statement.setString(1, "Wird zurückgerollt");
                    statement.setString(2, traegernummer);
                    statement.executeUpdate();
                }
                // Provoziert einen Fehler NACH der fachlichen Änderung, um Rollback + ERROR-Audit zu testen.
                throw new SQLException("Provozierter Fehler für den Audit-Invarianten-Test");
            })).isInstanceOf(OperatorAccessException.class);

        try (Connection connection = PostgresTestResource.openAdminConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT count(*) FROM schultraeger WHERE traegernummer = ?")) {
                statement.setString(1, traegernummer);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    assertThat(resultSet.getLong(1)).as("fachliche Änderung wurde zurückgerollt").isZero();
                }
            }
        }

        final List<String> outcomes = auditOutcomesFor(adminSubject, AuditAction.SCHULTRAEGER_CREATE);
        assertThat(outcomes).as("genau ein Audit-Eintrag, trotz Rollback persistiert").containsExactly("ERROR");
    }

    @Test
    void auditAdminVerweigertUpdateFuerAnwendungsrollen() throws Exception {
        try (Connection connection = PostgresTestResource.openConnectionAs("edugate_control",
                PostgresTestResource.CONTROL_PASSWORD)) {
            assertThatThrownBy(() -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE audit_admin SET outcome = 'SUCCESS'")) {
                    statement.executeUpdate();
                }
            }).isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
        }
    }

    @Test
    void auditAdminVerweigertDeleteFuerAnwendungsrollen() throws Exception {
        try (Connection connection = PostgresTestResource.openConnectionAs("edugate_operator",
                PostgresTestResource.OPERATOR_PASSWORD)) {
            assertThatThrownBy(() -> {
                try (PreparedStatement statement = connection.prepareStatement("DELETE FROM audit_admin")) {
                    statement.executeUpdate();
                }
            }).isInstanceOf(SQLException.class).hasMessageContaining("permission denied");
        }
    }

    private List<String> auditOutcomesFor(final String adminSubject, final AuditAction action) throws SQLException {
        try (Connection connection = PostgresTestResource.openAdminConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT outcome FROM audit_admin WHERE admin_subject = ? AND action = ?")) {
            statement.setString(1, adminSubject);
            statement.setString(2, action.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                final List<String> outcomes = new ArrayList<>();
                while (resultSet.next()) {
                    outcomes.add(resultSet.getString("outcome"));
                }
                return outcomes;
            }
        }
    }
}
