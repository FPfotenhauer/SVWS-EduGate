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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fachliche Operationen für SVWS-Instanzen. Eine SVWS-Instanz ist eine mandantenübergreifende
 * Betriebsressource ({@link de.svws_nrw.edugate.core.domain.SvwsInstanz}, ADR-011) - wie die
 * Mandanten-Wurzel {@code schultraeger} läuft daher jede Operation über {@link OperatorAccess}
 * (ADR-009), nicht über den tenant-gebundenen Standardpfad.
 */
@ApplicationScoped
public class SvwsInstanzService {

    private static final String POSTGRES_UNIQUE_VIOLATION = "23505";

    private static final String SELECT_COLUMNS =
        "id, name, base_url, status, aktiv, credentials_encrypted, created_at, updated_at";

    @Inject
    OperatorAccess operatorAccess;

    @Inject
    SecretStore secretStore;

    public SvwsInstanzPageDto list(final String adminSubject, final int page, final int size, final String query) {
        return operatorAccess.execute(adminSubject, AuditAction.SVWS_INSTANZ_LIST, "svws_instanz", connection -> {
            final String filter = (query == null || query.isBlank()) ? null : query.trim();

            final List<SvwsInstanzDto> items = new ArrayList<>();
            final String selectSql = "SELECT " + SELECT_COLUMNS + " FROM svws_instanz "
                + "WHERE (?::text IS NULL OR name ILIKE '%' || ?::text || '%' OR base_url ILIKE '%' || ?::text || '%') "
                + "ORDER BY name LIMIT ? OFFSET ?";
            try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
                statement.setString(1, filter);
                statement.setString(2, filter);
                statement.setString(3, filter);
                statement.setInt(4, size);
                statement.setInt(5, page * size);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        items.add(toDto(resultSet));
                    }
                }
            }

            final String countSql = "SELECT count(*) FROM svws_instanz "
                + "WHERE (?::text IS NULL OR name ILIKE '%' || ?::text || '%' OR base_url ILIKE '%' || ?::text || '%')";
            long total;
            try (PreparedStatement statement = connection.prepareStatement(countSql)) {
                statement.setString(1, filter);
                statement.setString(2, filter);
                statement.setString(3, filter);
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
            final String sql = "INSERT INTO svws_instanz (name, base_url) VALUES (?, ?) RETURNING " + SELECT_COLUMNS;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, request.name());
                statement.setString(2, request.baseUrl());
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    final SvwsInstanzDto dto = toDto(resultSet);
                    return new OperatorOutcome<>(dto, dto.id(), null, null);
                }
            } catch (final SQLException e) {
                if (POSTGRES_UNIQUE_VIOLATION.equals(e.getSQLState())) {
                    throw new OperatorConflictException(
                        "Eine SVWS-Instanz mit der Base-URL '" + request.baseUrl() + "' existiert bereits.");
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
            final String sql = "UPDATE svws_instanz SET name = ?, base_url = ?, status = ?, aktiv = ?, updated_at = now() "
                + "WHERE id = ? RETURNING " + SELECT_COLUMNS;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, request.name());
                statement.setString(2, request.baseUrl());
                statement.setString(3, request.status().name());
                statement.setBoolean(4, request.aktiv());
                statement.setObject(5, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new OperatorNotFoundException("SVWS-Instanz '" + id + "' wurde nicht gefunden.");
                    }
                    final SvwsInstanzDto dto = toDto(resultSet);
                    return new OperatorOutcome<>(dto, id, null, null);
                }
            } catch (final SQLException e) {
                if (POSTGRES_UNIQUE_VIOLATION.equals(e.getSQLState())) {
                    throw new OperatorConflictException(
                        "Eine SVWS-Instanz mit der Base-URL '" + request.baseUrl() + "' existiert bereits.");
                }
                throw e;
            }
        });
    }

    public void setCredentials(final String adminSubject, final UUID id, final SvwsInstanzCredentialsRequest request) {
        final byte[] plaintext = (request.username() + ":" + request.password()).getBytes(StandardCharsets.UTF_8);
        final byte[] encrypted = secretStore.encrypt(plaintext);

        operatorAccess.execute(adminSubject, AuditAction.SVWS_INSTANZ_CREDENTIALS_SET, "svws_instanz", connection -> {
            final String sql = "UPDATE svws_instanz SET credentials_encrypted = ?, updated_at = now() WHERE id = ?";
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

    private SvwsInstanzDto toDto(final ResultSet resultSet) throws SQLException {
        return new SvwsInstanzDto(
            (UUID) resultSet.getObject("id"),
            resultSet.getString("name"),
            resultSet.getString("base_url"),
            InstanzStatus.valueOf(resultSet.getString("status")),
            resultSet.getBoolean("aktiv"),
            resultSet.getBytes("credentials_encrypted") != null,
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getTimestamp("updated_at").toInstant());
    }
}
