package de.svws_nrw.edugate.control.schemaumgebung;

import de.svws_nrw.edugate.control.operator.AuditAction;
import de.svws_nrw.edugate.control.operator.OperatorAccess;
import de.svws_nrw.edugate.control.operator.OperatorConflictException;
import de.svws_nrw.edugate.control.operator.OperatorDeniedException;
import de.svws_nrw.edugate.control.operator.OperatorNotFoundException;
import de.svws_nrw.edugate.control.operator.OperatorOutcome;
import de.svws_nrw.edugate.control.schemaumgebung.dto.SchemaUmgebungCreateRequest;
import de.svws_nrw.edugate.control.schemaumgebung.dto.SchemaUmgebungDto;
import de.svws_nrw.edugate.control.schemaumgebung.dto.SchemaUmgebungUpdateRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Verwaltung der vom Betreiber gepflegten Schema-Umgebungen (ADR-012: Namenskonvention ist eine
 * Betreiber-Konvention, kein geschlossenes Enum). {@code schema_umgebung} ist wie
 * {@code svws_instanz} eine mandantenübergreifende Betriebsressource (ADR-011-Muster) - alle
 * Operationen laufen daher über {@link OperatorAccess}, nicht über den tenant-gebundenen
 * Standardpfad. Diese Verwaltung ergänzt den in ADR-009 als "abschließend" bezeichneten Katalog
 * erlaubter Operator-Use-Cases um einen weiteren, hier explizit benannten Fall (Verwaltung einer
 * weiteren mandantenübergreifenden Ressource, analog zu {@code svws_instanz}).
 *
 * <p>{@code system}-Einträge (aktuell nur {@code PRODUKTIV}) sind reserviert: Name und
 * Aktiv-Status sind unveränderlich, weil die Namenskonvention (kein Suffix) und die Regel
 * "höchstens ein aktives Produktiv-Schema je Schule" (ADR-012) darauf als festen String
 * verweisen - nur die Beschreibung bleibt bearbeitbar. {@code list} liefert auch inaktive
 * Umgebungen zurück (Verwaltungssicht); der tenant-gebundene Namensvorschlag/das
 * Schema-Anlegen-Formular filtert clientseitig auf {@code aktiv}.
 */
@ApplicationScoped
public class SchemaUmgebungService {

    private static final String POSTGRES_UNIQUE_VIOLATION = "23505";
    private static final String NAME_UNIQUE_CONSTRAINT = "schema_umgebung_name_unique";

    private static final String SELECT_COLUMNS = "id, name, system, beschreibung, aktiv, created_at, updated_at";

    @Inject
    OperatorAccess operatorAccess;

    public List<SchemaUmgebungDto> list(final String adminSubject) {
        return operatorAccess.execute(adminSubject, AuditAction.SCHEMA_UMGEBUNG_LIST, "schema_umgebung", connection -> {
            final List<SchemaUmgebungDto> items = new ArrayList<>();
            final String sql = "SELECT " + SELECT_COLUMNS + " FROM schema_umgebung ORDER BY system DESC, name";
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    items.add(toDto(resultSet));
                }
            }
            return OperatorOutcome.crossTenant(items, null);
        });
    }

    public SchemaUmgebungDto create(final String adminSubject, final SchemaUmgebungCreateRequest request) {
        return operatorAccess.execute(adminSubject, AuditAction.SCHEMA_UMGEBUNG_CREATE, "schema_umgebung", connection -> {
            final String sql = "INSERT INTO schema_umgebung (name, system, beschreibung) VALUES (?, false, ?) "
                + "RETURNING " + SELECT_COLUMNS;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, normalisieren(request.name()));
                statement.setString(2, request.beschreibung());
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    final SchemaUmgebungDto dto = toDto(resultSet);
                    return new OperatorOutcome<>(dto, dto.id(), null, null);
                }
            } catch (final SQLException e) {
                if (POSTGRES_UNIQUE_VIOLATION.equals(e.getSQLState())) {
                    throw conflictFor(request.name());
                }
                throw e;
            }
        });
    }

    public SchemaUmgebungDto update(final String adminSubject, final UUID id, final SchemaUmgebungUpdateRequest request) {
        return operatorAccess.execute(adminSubject, AuditAction.SCHEMA_UMGEBUNG_UPDATE, "schema_umgebung", connection -> {
            final SchemaUmgebungDto bestehende = selectById(connection, id);
            final String neuerName = normalisieren(request.name());
            if (bestehende.system() && (!bestehende.name().equals(neuerName) || !request.aktiv())) {
                throw new OperatorDeniedException(
                    "Die Systemumgebung '" + bestehende.name() + "' kann nicht umbenannt oder deaktiviert werden.");
            }

            final String sql = "UPDATE schema_umgebung SET name = ?, beschreibung = ?, aktiv = ?, updated_at = now() "
                + "WHERE id = ? RETURNING " + SELECT_COLUMNS;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, neuerName);
                statement.setString(2, request.beschreibung());
                statement.setBoolean(3, request.aktiv());
                statement.setObject(4, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    final SchemaUmgebungDto dto = toDto(resultSet);
                    return new OperatorOutcome<>(dto, id, null, null);
                }
            } catch (final SQLException e) {
                if (POSTGRES_UNIQUE_VIOLATION.equals(e.getSQLState())) {
                    throw conflictFor(request.name());
                }
                throw e;
            }
        });
    }

    /** Deaktiviert eine Umgebung (ADR-013: gefährliche Operation). Systemumgebungen ausgenommen. */
    public SchemaUmgebungDto deactivate(final String adminSubject, final UUID id) {
        return operatorAccess.execute(adminSubject, AuditAction.SCHEMA_UMGEBUNG_DEACTIVATE, "schema_umgebung", connection -> {
            final SchemaUmgebungDto bestehende = selectById(connection, id);
            if (bestehende.system()) {
                throw new OperatorDeniedException(
                    "Die Systemumgebung '" + bestehende.name() + "' kann nicht deaktiviert werden.");
            }

            final String sql = "UPDATE schema_umgebung SET aktiv = false, updated_at = now() "
                + "WHERE id = ? RETURNING " + SELECT_COLUMNS;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    final SchemaUmgebungDto dto = toDto(resultSet);
                    return new OperatorOutcome<>(dto, id, null, null);
                }
            }
        });
    }

    private SchemaUmgebungDto selectById(final java.sql.Connection connection, final UUID id) throws SQLException {
        final String sql = "SELECT " + SELECT_COLUMNS + " FROM schema_umgebung WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new OperatorNotFoundException("Schema-Umgebung '" + id + "' wurde nicht gefunden.");
                }
                return toDto(resultSet);
            }
        }
    }

    private String normalisieren(final String name) {
        return name.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private OperatorConflictException conflictFor(final String name) {
        return new OperatorConflictException("Eine Umgebung mit dem Namen '" + normalisieren(name) + "' existiert bereits.");
    }

    private SchemaUmgebungDto toDto(final ResultSet resultSet) throws SQLException {
        return new SchemaUmgebungDto(
            (UUID) resultSet.getObject("id"),
            resultSet.getString("name"),
            resultSet.getBoolean("system"),
            resultSet.getString("beschreibung"),
            resultSet.getBoolean("aktiv"),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getTimestamp("updated_at").toInstant());
    }
}
