package de.svws_nrw.edugate.control.schultraeger;

import de.svws_nrw.edugate.control.operator.AuditAction;
import de.svws_nrw.edugate.control.operator.OperatorAccess;
import de.svws_nrw.edugate.control.operator.OperatorConflictException;
import de.svws_nrw.edugate.control.operator.OperatorNotFoundException;
import de.svws_nrw.edugate.control.operator.OperatorOutcome;
import de.svws_nrw.edugate.control.schultraeger.dto.SchultraegerCreateRequest;
import de.svws_nrw.edugate.control.schultraeger.dto.SchultraegerDto;
import de.svws_nrw.edugate.control.schultraeger.dto.SchultraegerPageDto;
import de.svws_nrw.edugate.control.schultraeger.dto.SchultraegerUpdateRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fachliche Operationen für Schulträger. Schulträger-CRUD ist per Definition
 * mandantenübergreifend (die Mandanten-Wurzel selbst), daher laufen alle Operationen über
 * {@link OperatorAccess} (ADR-009).
 */
@ApplicationScoped
public class SchultraegerService {

    private static final String POSTGRES_UNIQUE_VIOLATION = "23505";

    private static final String SELECT_COLUMNS =
        "id, name, traegernummer, strasse, plz, ort, beschreibung, aktiv, created_at, updated_at";

    @Inject
    OperatorAccess operatorAccess;

    public SchultraegerPageDto list(final String adminSubject, final int page, final int size, final String query) {
        return operatorAccess.execute(adminSubject, AuditAction.SCHULTRAEGER_LIST, "schultraeger", connection -> {
            final String filter = (query == null || query.isBlank()) ? null : query.trim();

            final List<SchultraegerDto> items = new ArrayList<>();
            final String selectSql = "SELECT " + SELECT_COLUMNS + " FROM schultraeger "
                + "WHERE (?::text IS NULL OR name ILIKE '%' || ?::text || '%' OR traegernummer ILIKE '%' || ?::text || '%') "
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

            final String countSql = "SELECT count(*) FROM schultraeger "
                + "WHERE (?::text IS NULL OR name ILIKE '%' || ?::text || '%' OR traegernummer ILIKE '%' || ?::text || '%')";
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

            final SchultraegerPageDto result = new SchultraegerPageDto(items, page, size, total);
            return OperatorOutcome.crossTenant(result, null);
        });
    }

    public SchultraegerDto create(final String adminSubject, final SchultraegerCreateRequest request) {
        return operatorAccess.execute(adminSubject, AuditAction.SCHULTRAEGER_CREATE, "schultraeger", connection -> {
            final String sql = "INSERT INTO schultraeger (name, traegernummer, strasse, plz, ort, beschreibung) "
                + "VALUES (?, ?, ?, ?, ?, ?) RETURNING " + SELECT_COLUMNS;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, request.name());
                statement.setString(2, request.traegernummer());
                statement.setString(3, request.strasse());
                statement.setString(4, request.plz());
                statement.setString(5, request.ort());
                statement.setString(6, request.beschreibung());
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    final SchultraegerDto dto = toDto(resultSet);
                    return OperatorOutcome.of(dto, dto.id());
                }
            } catch (final SQLException e) {
                if (POSTGRES_UNIQUE_VIOLATION.equals(e.getSQLState())) {
                    throw new OperatorConflictException(
                        "Ein Schulträger mit der Trägernummer '" + request.traegernummer() + "' existiert bereits.");
                }
                throw e;
            }
        });
    }

    public SchultraegerDto get(final String adminSubject, final UUID id) {
        return operatorAccess.execute(adminSubject, AuditAction.SCHULTRAEGER_READ, "schultraeger", connection -> {
            final SchultraegerDto dto = selectById(connection, id);
            return OperatorOutcome.of(dto, id);
        });
    }

    public SchultraegerDto update(final String adminSubject, final UUID id, final SchultraegerUpdateRequest request) {
        return operatorAccess.execute(adminSubject, AuditAction.SCHULTRAEGER_UPDATE, "schultraeger", connection -> {
            final String sql = "UPDATE schultraeger SET name = ?, traegernummer = ?, strasse = ?, plz = ?, ort = ?, "
                + "beschreibung = ?, updated_at = now() WHERE id = ? RETURNING " + SELECT_COLUMNS;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, request.name());
                statement.setString(2, request.traegernummer());
                statement.setString(3, request.strasse());
                statement.setString(4, request.plz());
                statement.setString(5, request.ort());
                statement.setString(6, request.beschreibung());
                statement.setObject(7, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new OperatorNotFoundException("Schulträger '" + id + "' wurde nicht gefunden.");
                    }
                    final SchultraegerDto dto = toDto(resultSet);
                    return OperatorOutcome.of(dto, id);
                }
            } catch (final SQLException e) {
                if (POSTGRES_UNIQUE_VIOLATION.equals(e.getSQLState())) {
                    throw new OperatorConflictException(
                        "Ein Schulträger mit der Trägernummer '" + request.traegernummer() + "' existiert bereits.");
                }
                throw e;
            }
        });
    }

    public void deactivate(final String adminSubject, final UUID id) {
        operatorAccess.execute(adminSubject, AuditAction.SCHULTRAEGER_DEACTIVATE, "schultraeger", connection -> {
            final String sql = "UPDATE schultraeger SET aktiv = false, updated_at = now() "
                + "WHERE id = ? RETURNING " + SELECT_COLUMNS;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new OperatorNotFoundException("Schulträger '" + id + "' wurde nicht gefunden.");
                    }
                    final SchultraegerDto dto = toDto(resultSet);
                    return OperatorOutcome.of(dto, id);
                }
            }
        });
    }

    public SchultraegerDto reactivate(final String adminSubject, final UUID id) {
        return operatorAccess.execute(adminSubject, AuditAction.SCHULTRAEGER_REACTIVATE, "schultraeger", connection -> {
            final String sql = "UPDATE schultraeger SET aktiv = true, updated_at = now() "
                + "WHERE id = ? RETURNING " + SELECT_COLUMNS;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new OperatorNotFoundException("Schulträger '" + id + "' wurde nicht gefunden.");
                    }
                    final SchultraegerDto dto = toDto(resultSet);
                    return OperatorOutcome.of(dto, id);
                }
            }
        });
    }

    private SchultraegerDto selectById(final java.sql.Connection connection, final UUID id) throws SQLException {
        final String sql = "SELECT " + SELECT_COLUMNS + " FROM schultraeger WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new OperatorNotFoundException("Schulträger '" + id + "' wurde nicht gefunden.");
                }
                return toDto(resultSet);
            }
        }
    }

    private SchultraegerDto toDto(final ResultSet resultSet) throws SQLException {
        return new SchultraegerDto(
            (UUID) resultSet.getObject("id"),
            resultSet.getString("name"),
            resultSet.getString("traegernummer"),
            resultSet.getString("strasse"),
            resultSet.getString("plz"),
            resultSet.getString("ort"),
            resultSet.getString("beschreibung"),
            resultSet.getBoolean("aktiv"),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getTimestamp("updated_at").toInstant());
    }
}
