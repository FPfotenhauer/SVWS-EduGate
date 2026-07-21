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
    private static final String POSTGRES_FOREIGN_KEY_VIOLATION = "23503";

    private static final String SELECT_COLUMNS = "id, name, traegernummer, strasse, plz, ort, beschreibung, aktiv, "
        + "katalog_id, quelle, sonderfall_hinweis, created_at, updated_at";

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
            // Herkunft wird serverseitig entschieden, nicht dem Client überlassen (ADR-020
            // "Schritt 2"): eine katalogId erzwingt LANDESLISTE, unabhängig vom sonderfall-Flag.
            final String quelle = request.katalogId() != null ? "LANDESLISTE" : request.sonderfall() ? "SONDERFALL" : "MANUELL";
            final String sonderfallHinweis = "SONDERFALL".equals(quelle) ? request.sonderfallHinweis() : null;

            final String sql = "INSERT INTO schultraeger "
                + "(name, traegernummer, strasse, plz, ort, beschreibung, katalog_id, quelle, sonderfall_hinweis) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING " + SELECT_COLUMNS;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, request.name());
                statement.setString(2, request.traegernummer());
                statement.setString(3, request.strasse());
                statement.setString(4, request.plz());
                statement.setString(5, request.ort());
                statement.setString(6, request.beschreibung());
                statement.setObject(7, request.katalogId());
                statement.setString(8, quelle);
                statement.setString(9, sonderfallHinweis);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    final SchultraegerDto dto = toDto(resultSet);
                    final String detailsJson = "{\"quelle\":\"" + quelle + "\"}";
                    return new OperatorOutcome<>(dto, dto.id(), dto.id(), detailsJson);
                }
            } catch (final SQLException e) {
                if (POSTGRES_UNIQUE_VIOLATION.equals(e.getSQLState())) {
                    throw new OperatorConflictException(
                        "Ein Schulträger mit der Trägernummer '" + request.traegernummer() + "' existiert bereits.");
                }
                if (POSTGRES_FOREIGN_KEY_VIOLATION.equals(e.getSQLState())) {
                    throw new OperatorNotFoundException(
                        "Der referenzierte Schuldatei-Katalogeintrag '" + request.katalogId() + "' wurde nicht gefunden.");
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

    /**
     * Endgültiges (hartes) Löschen, im Unterschied zu {@link #deactivate}. Nur für Schulträger
     * ohne Schulen und ohne Landeslisten-Bezug (quelle MANUELL/SONDERFALL): Landeslisten-Einträge
     * bleiben aus Provenienzgründen immer geschützt, und Schulen (bzw. davon abhängige Schemata)
     * müssen vorher verschoben, archiviert oder gelöscht werden.
     */
    public void delete(final String adminSubject, final UUID id) {
        operatorAccess.execute(adminSubject, AuditAction.SCHULTRAEGER_DELETE, "schultraeger", connection -> {
            final SchultraegerDto vorher = selectById(connection, id);

            final long schulenCount = countSchulen(connection, id);
            if (schulenCount > 0) {
                throw new OperatorConflictException("Dieser Schulträger hat noch " + schulenCount
                    + (schulenCount == 1 ? " Schule" : " Schulen")
                    + ". Bitte zuerst die Schulen verschieben, archivieren oder löschen.");
            }
            if (!"MANUELL".equals(vorher.quelle()) && !"SONDERFALL".equals(vorher.quelle())) {
                throw new OperatorConflictException(
                    "Nur manuell angelegte Schulträger ohne Landeslisten-Bezug können endgültig gelöscht werden.");
            }

            try (PreparedStatement cleanup = connection.prepareStatement("DELETE FROM ansprechpartner WHERE tenant_id = ?")) {
                cleanup.setObject(1, id);
                cleanup.executeUpdate();
            }

            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM schultraeger WHERE id = ?")) {
                statement.setObject(1, id);
                statement.executeUpdate();
            } catch (final SQLException e) {
                if (POSTGRES_FOREIGN_KEY_VIOLATION.equals(e.getSQLState())) {
                    throw new OperatorConflictException("Dieser Schulträger kann nicht gelöscht werden, da noch "
                        + "abhängige Datensätze existieren (z. B. registrierte SVWS-Instanzen).");
                }
                throw e;
            }

            return new OperatorOutcome<>(Boolean.TRUE, id, id, null);
        });
    }

    private long countSchulen(final java.sql.Connection connection, final UUID tenantId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT count(*) FROM schule WHERE tenant_id = ?")) {
            statement.setObject(1, tenantId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
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
            (UUID) resultSet.getObject("katalog_id"),
            resultSet.getString("quelle"),
            resultSet.getString("sonderfall_hinweis"),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getTimestamp("updated_at").toInstant());
    }
}
