package de.svws_nrw.edugate.control.operator;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.jboss.logging.Logger;

/**
 * Einziger erlaubter Zugriffspfad auf die {@code operator}-Datasource (Rolle
 * {@code edugate_operator}), gemäß ADR-009. Diese Klasse darf ausschließlich innerhalb des
 * Packages {@code de.svws_nrw.edugate.control.operator} liegen – ein ArchUnit-Test erzwingt,
 * dass kein Code außerhalb dieses Packages die {@code operator}-Datasource injiziert.
 *
 * <p>Transaktionsregel (ADR-009): Bei Erfolg wird der {@code audit_admin}-Eintrag atomar in
 * derselben Transaktion wie die fachliche Änderung geschrieben. Bei {@code DENIED}/{@code ERROR}
 * wird die Fachtransaktion zurückgerollt und der Audit-Eintrag anschließend in einer neuen,
 * unabhängigen Transaktion geschrieben, damit er das Rollback überlebt.
 */
@ApplicationScoped
public class OperatorAccess {

    private static final Logger LOG = Logger.getLogger(OperatorAccess.class);

    private static final String INSERT_AUDIT = """
        INSERT INTO audit_admin (admin_subject, action, entity_type, entity_id, tenant_id, outcome, details)
        VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
        """;

    @Inject
    @DataSource("operator")
    AgroalDataSource operatorDataSource;

    /**
     * Führt {@code operation} als Operator-Zugriff aus und auditiert das Ergebnis.
     *
     * @throws OperatorNotFoundException wenn die Operation dies wirft (Audit: ERROR)
     * @throws OperatorDeniedException   wenn die Operation dies wirft (Audit: DENIED)
     * @throws OperatorAccessException   bei technischen Fehlern (Audit: ERROR)
     */
    public <T> T execute(final String adminSubject, final AuditAction action, final String entityType,
            final OperatorOperation<T> operation) {
        try (Connection connection = operatorDataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                final OperatorOutcome<T> outcome = operation.execute(connection);
                writeAudit(connection, adminSubject, action, entityType, outcome.entityId(), outcome.tenantId(),
                    AuditOutcome.SUCCESS, outcome.detailsJson());
                connection.commit();
                return outcome.value();
            } catch (final Exception e) {
                safeRollback(connection);
                final AuditOutcome auditOutcome = e instanceof OperatorDeniedException ? AuditOutcome.DENIED : AuditOutcome.ERROR;
                writeAuditInNewTransaction(adminSubject, action, entityType, null, null, auditOutcome, null);
                if (e instanceof OperatorNotFoundException notFound) {
                    throw notFound;
                }
                if (e instanceof OperatorDeniedException denied) {
                    throw denied;
                }
                if (e instanceof OperatorConflictException conflict) {
                    throw conflict;
                }
                throw new OperatorAccessException("OperatorAccess-Operation fehlgeschlagen: " + action, e);
            }
        } catch (final SQLException e) {
            writeAuditInNewTransaction(adminSubject, action, entityType, null, null, AuditOutcome.ERROR, null);
            throw new OperatorAccessException("Verbindung zur operator-Datasource fehlgeschlagen.", e);
        }
    }

    private void writeAudit(final Connection connection, final String adminSubject, final AuditAction action,
            final String entityType, final UUID entityId, final UUID tenantId, final AuditOutcome outcome,
            final String detailsJson) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_AUDIT)) {
            statement.setString(1, adminSubject);
            statement.setString(2, action.name());
            statement.setString(3, entityType);
            statement.setObject(4, entityId);
            statement.setObject(5, tenantId);
            statement.setString(6, outcome.name());
            statement.setString(7, detailsJson);
            statement.executeUpdate();
        }
    }

    private void writeAuditInNewTransaction(final String adminSubject, final AuditAction action,
            final String entityType, final UUID entityId, final UUID tenantId, final AuditOutcome outcome,
            final String detailsJson) {
        try (Connection connection = operatorDataSource.getConnection()) {
            connection.setAutoCommit(false);
            writeAudit(connection, adminSubject, action, entityType, entityId, tenantId, outcome, detailsJson);
            connection.commit();
        } catch (final SQLException e) {
            // Rückfallebene gemäß ADR-009: Wenn selbst der unabhängige Audit-Schreibpfad
            // scheitert, landet der Vorgang wenigstens im strukturierten Anwendungslog.
            LOG.errorf(e, "Audit-Eintrag konnte nicht geschrieben werden: subject=%s action=%s outcome=%s",
                adminSubject, action, outcome);
        }
    }

    private void safeRollback(final Connection connection) {
        try {
            connection.rollback();
        } catch (final SQLException e) {
            LOG.error("Rollback der Fachtransaktion fehlgeschlagen.", e);
        }
    }
}
