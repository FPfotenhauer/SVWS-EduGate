package de.svws_nrw.edugate.control.svwsinstanz;

import de.svws_nrw.edugate.control.operator.AuditAction;
import de.svws_nrw.edugate.control.operator.OperatorAccess;
import de.svws_nrw.edugate.control.operator.OperatorConflictException;
import de.svws_nrw.edugate.control.operator.OperatorNotFoundException;
import de.svws_nrw.edugate.control.operator.OperatorOutcome;
import de.svws_nrw.edugate.control.svwsinstanz.dto.SvwsInstanzCreateRequest;
import de.svws_nrw.edugate.control.svwsinstanz.dto.SvwsInstanzCredentialsRequest;
import de.svws_nrw.edugate.control.svwsinstanz.dto.SvwsInstanzDto;
import de.svws_nrw.edugate.control.svwsinstanz.dto.SvwsInstanzPageDto;
import de.svws_nrw.edugate.control.svwsinstanz.dto.SvwsInstanzUpdateRequest;
import de.svws_nrw.edugate.core.domain.InstanzStatus;
import de.svws_nrw.edugate.core.secret.SecretStore;
import de.svws_nrw.edugate.core.svws.SvwsConnectionTestResult;
import de.svws_nrw.edugate.core.svws.SvwsConnectionTester;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.postgresql.util.PSQLException;

/**
 * Fachliche Operationen für SVWS-Instanzen. Eine SVWS-Instanz ist eine mandantenübergreifende
 * Betriebsressource ({@link de.svws_nrw.edugate.core.domain.SvwsInstanz}, ADR-011) - wie die
 * Mandanten-Wurzel {@code schultraeger} läuft daher jede Operation über {@link OperatorAccess}
 * (ADR-009), nicht über den tenant-gebundenen Standardpfad.
 */
@ApplicationScoped
public class SvwsInstanzService {

    private static final String POSTGRES_UNIQUE_VIOLATION = "23505";
    private static final String NAME_UNIQUE_CONSTRAINT = "svws_instanz_name_unique";
    private static final String BASE_URL_UNIQUE_CONSTRAINT = "svws_instanz_base_url_unique";

    private static final String SELECT_COLUMNS =
        "id, name, base_url, beschreibung, status, aktiv, credentials_encrypted, credentials_updated_at, "
            + "last_connection_test_at, last_connection_test_success, last_connection_test_message, "
            + "created_at, updated_at";

    @Inject
    OperatorAccess operatorAccess;

    @Inject
    SecretStore secretStore;

    @Inject
    SvwsConnectionTester connectionTester;

    public SvwsInstanzPageDto list(final String adminSubject, final int page, final int size, final String query,
            final InstanzStatus status) {
        return operatorAccess.execute(adminSubject, AuditAction.SVWS_INSTANZ_LIST, "svws_instanz", connection -> {
            final String filter = (query == null || query.isBlank()) ? null : query.trim();
            final String statusFilter = status == null ? null : status.name();

            final List<SvwsInstanzDto> items = new ArrayList<>();
            final String selectSql = "SELECT " + SELECT_COLUMNS + " FROM svws_instanz "
                + "WHERE (?::text IS NULL OR name ILIKE '%' || ?::text || '%' "
                + "OR base_url ILIKE '%' || ?::text || '%' OR beschreibung ILIKE '%' || ?::text || '%') "
                + "AND (?::text IS NULL OR status = ?::text) "
                + "ORDER BY name LIMIT ? OFFSET ?";
            try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
                statement.setString(1, filter);
                statement.setString(2, filter);
                statement.setString(3, filter);
                statement.setString(4, filter);
                statement.setString(5, statusFilter);
                statement.setString(6, statusFilter);
                statement.setInt(7, size);
                statement.setInt(8, page * size);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        items.add(toDto(resultSet));
                    }
                }
            }

            final String countSql = "SELECT count(*) FROM svws_instanz "
                + "WHERE (?::text IS NULL OR name ILIKE '%' || ?::text || '%' "
                + "OR base_url ILIKE '%' || ?::text || '%' OR beschreibung ILIKE '%' || ?::text || '%') "
                + "AND (?::text IS NULL OR status = ?::text)";
            long total;
            try (PreparedStatement statement = connection.prepareStatement(countSql)) {
                statement.setString(1, filter);
                statement.setString(2, filter);
                statement.setString(3, filter);
                statement.setString(4, filter);
                statement.setString(5, statusFilter);
                statement.setString(6, statusFilter);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    total = resultSet.getLong(1);
                }
            }

            final SvwsInstanzPageDto result = new SvwsInstanzPageDto(items, page, size, total);
            return OperatorOutcome.crossTenant(result, null);
        });
    }

    public SvwsInstanzDto create(final String adminSubject, final SvwsInstanzCreateRequest request) {
        return operatorAccess.execute(adminSubject, AuditAction.SVWS_INSTANZ_CREATE, "svws_instanz", connection -> {
            final String sql = "INSERT INTO svws_instanz (name, base_url, beschreibung) VALUES (?, ?, ?) "
                + "RETURNING " + SELECT_COLUMNS;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, request.name());
                statement.setString(2, request.baseUrl());
                statement.setString(3, request.beschreibung());
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    final SvwsInstanzDto dto = toDto(resultSet);
                    return new OperatorOutcome<>(dto, dto.id(), null, null);
                }
            } catch (final SQLException e) {
                if (POSTGRES_UNIQUE_VIOLATION.equals(e.getSQLState())) {
                    throw conflictFor(e, request.name(), request.baseUrl());
                }
                throw e;
            }
        });
    }

    public SvwsInstanzDto get(final String adminSubject, final UUID id) {
        return operatorAccess.execute(adminSubject, AuditAction.SVWS_INSTANZ_READ, "svws_instanz", connection -> {
            final SvwsInstanzDto dto = selectById(connection, id);
            return new OperatorOutcome<>(dto, id, null, null);
        });
    }

    public SvwsInstanzDto update(final String adminSubject, final UUID id, final SvwsInstanzUpdateRequest request) {
        return operatorAccess.execute(adminSubject, AuditAction.SVWS_INSTANZ_UPDATE, "svws_instanz", connection -> {
            final String sql = "UPDATE svws_instanz SET name = ?, base_url = ?, beschreibung = ?, status = ?, "
                + "aktiv = ?, updated_at = now() WHERE id = ? RETURNING " + SELECT_COLUMNS;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, request.name());
                statement.setString(2, request.baseUrl());
                statement.setString(3, request.beschreibung());
                statement.setString(4, request.status().name());
                statement.setBoolean(5, request.aktiv());
                statement.setObject(6, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new OperatorNotFoundException("SVWS-Instanz '" + id + "' wurde nicht gefunden.");
                    }
                    final SvwsInstanzDto dto = toDto(resultSet);
                    return new OperatorOutcome<>(dto, id, null, null);
                }
            } catch (final SQLException e) {
                if (POSTGRES_UNIQUE_VIOLATION.equals(e.getSQLState())) {
                    throw conflictFor(e, request.name(), request.baseUrl());
                }
                throw e;
            }
        });
    }

    public void setCredentials(final String adminSubject, final UUID id, final SvwsInstanzCredentialsRequest request) {
        final byte[] plaintext = (request.username() + ":" + request.password()).getBytes(StandardCharsets.UTF_8);
        final byte[] encrypted = secretStore.encrypt(plaintext);

        operatorAccess.execute(adminSubject, AuditAction.SVWS_INSTANZ_CREDENTIALS_SET, "svws_instanz", connection -> {
            final String sql = "UPDATE svws_instanz SET credentials_encrypted = ?, credentials_updated_at = now(), "
                + "updated_at = now() WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setBytes(1, encrypted);
                statement.setObject(2, id);
                final int updated = statement.executeUpdate();
                if (updated == 0) {
                    throw new OperatorNotFoundException("SVWS-Instanz '" + id + "' wurde nicht gefunden.");
                }
                return new OperatorOutcome<>(Boolean.TRUE, id, null, null);
            }
        });
    }

    public void deactivate(final String adminSubject, final UUID id) {
        operatorAccess.execute(adminSubject, AuditAction.SVWS_INSTANZ_DEACTIVATE, "svws_instanz", connection -> {
            final String sql = "UPDATE svws_instanz SET aktiv = false, updated_at = now() "
                + "WHERE id = ? RETURNING " + SELECT_COLUMNS;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new OperatorNotFoundException("SVWS-Instanz '" + id + "' wurde nicht gefunden.");
                    }
                    final SvwsInstanzDto dto = toDto(resultSet);
                    return new OperatorOutcome<>(dto, id, null, null);
                }
            }
        });
    }

    /**
     * Führt einen Verbindungstest gegen die hinterlegte Base-URL aus (ADR-006: Entschlüsselung
     * nur unmittelbar vor dem Aufruf, im Speicher). Aktualisiert Erreichbarkeitsstatus,
     * Zeitpunkt und eine sichere Ergebnis-Meldung.
     *
     * <p>Sind Zugangsdaten hinterlegt, werden Erreichbarkeit <b>und</b> Gültigkeit/Rechte der
     * Zugangsdaten geprüft ({@link SvwsConnectionTester#test}); Erfolg setzt den Status auf
     * {@link InstanzStatus#OK}. Sind keine Zugangsdaten hinterlegt, wird nur die reine
     * Erreichbarkeit geprüft ({@link SvwsConnectionTester#testReachability}); Erfolg setzt den
     * Status auf {@link InstanzStatus#DEGRADED} (erreichbar, aber ungeprüfte Zugangsdaten) statt
     * {@code OK}. In beiden Fällen setzt ein technischer Fehlschlag den Status auf
     * {@link InstanzStatus#UNREACHABLE}.
     */
    public SvwsInstanzDto testConnection(final String adminSubject, final UUID id) {
        return operatorAccess.execute(adminSubject, AuditAction.SVWS_INSTANZ_CONNECTION_TEST, "svws_instanz", connection -> {
            final String baseUrl;
            final byte[] credentialsEncrypted;
            final String selectSql = "SELECT base_url, credentials_encrypted FROM svws_instanz WHERE id = ?";
            try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
                statement.setObject(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new OperatorNotFoundException("SVWS-Instanz '" + id + "' wurde nicht gefunden.");
                    }
                    baseUrl = resultSet.getString("base_url");
                    credentialsEncrypted = resultSet.getBytes("credentials_encrypted");
                }
            }

            final SvwsConnectionTestResult result;
            final InstanzStatus newStatus;
            if (credentialsEncrypted == null) {
                result = connectionTester.testReachability(baseUrl);
                newStatus = result.success() ? InstanzStatus.DEGRADED : InstanzStatus.UNREACHABLE;
            } else {
                final String decrypted = new String(secretStore.decrypt(credentialsEncrypted), StandardCharsets.UTF_8);
                final int separator = decrypted.indexOf(':');
                final String username = separator < 0 ? decrypted : decrypted.substring(0, separator);
                final String password = separator < 0 ? "" : decrypted.substring(separator + 1);
                result = connectionTester.test(baseUrl, username, password);
                newStatus = result.success() ? InstanzStatus.OK : InstanzStatus.UNREACHABLE;
            }

            final String updateSql = "UPDATE svws_instanz SET status = ?, last_connection_test_at = now(), "
                + "last_connection_test_success = ?, last_connection_test_message = ?, updated_at = now() "
                + "WHERE id = ? RETURNING " + SELECT_COLUMNS;
            try (PreparedStatement statement = connection.prepareStatement(updateSql)) {
                statement.setString(1, newStatus.name());
                statement.setBoolean(2, result.success());
                statement.setString(3, result.message());
                statement.setObject(4, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    final SvwsInstanzDto dto = toDto(resultSet);
                    final String detailsJson = "{\"success\":" + result.success() + "}";
                    return new OperatorOutcome<>(dto, id, null, detailsJson);
                }
            }
        });
    }

    private SvwsInstanzDto selectById(final Connection connection, final UUID id) throws SQLException {
        final String sql = "SELECT " + SELECT_COLUMNS + " FROM svws_instanz WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new OperatorNotFoundException("SVWS-Instanz '" + id + "' wurde nicht gefunden.");
                }
                return toDto(resultSet);
            }
        }
    }

    private OperatorConflictException conflictFor(final SQLException e, final String name, final String baseUrl) {
        final String constraint = constraintName(e);
        if (NAME_UNIQUE_CONSTRAINT.equals(constraint)) {
            return new OperatorConflictException(
                "Eine SVWS-Instanz mit der Kurzbezeichnung '" + name + "' existiert bereits.");
        }
        if (BASE_URL_UNIQUE_CONSTRAINT.equals(constraint)) {
            return new OperatorConflictException(
                "Eine SVWS-Instanz mit der Base-URL '" + baseUrl + "' existiert bereits.");
        }
        return new OperatorConflictException("Eine SVWS-Instanz mit diesen Werten existiert bereits.");
    }

    private String constraintName(final SQLException e) {
        if (e instanceof PSQLException psqlException && psqlException.getServerErrorMessage() != null) {
            return psqlException.getServerErrorMessage().getConstraint();
        }
        return null;
    }

    private SvwsInstanzDto toDto(final ResultSet resultSet) throws SQLException {
        return new SvwsInstanzDto(
            (UUID) resultSet.getObject("id"),
            resultSet.getString("name"),
            resultSet.getString("base_url"),
            resultSet.getString("beschreibung"),
            InstanzStatus.valueOf(resultSet.getString("status")),
            resultSet.getBoolean("aktiv"),
            resultSet.getBytes("credentials_encrypted") != null,
            toInstant(resultSet.getTimestamp("credentials_updated_at")),
            toInstant(resultSet.getTimestamp("last_connection_test_at")),
            (Boolean) resultSet.getObject("last_connection_test_success"),
            resultSet.getString("last_connection_test_message"),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getTimestamp("updated_at").toInstant());
    }

    private Instant toInstant(final Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
