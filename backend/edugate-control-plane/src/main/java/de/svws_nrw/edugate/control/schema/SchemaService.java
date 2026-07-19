package de.svws_nrw.edugate.control.schema;

import de.svws_nrw.edugate.control.operator.AuditAction;
import de.svws_nrw.edugate.control.operator.AuditOutcome;
import de.svws_nrw.edugate.control.operator.OperatorConflictException;
import de.svws_nrw.edugate.control.operator.OperatorNotFoundException;
import de.svws_nrw.edugate.control.schema.dto.SchemaCreateRequest;
import de.svws_nrw.edugate.control.schema.dto.SchemaDto;
import de.svws_nrw.edugate.control.schema.dto.SchemaNamingSuggestionDto;
import de.svws_nrw.edugate.control.schema.dto.SchemaUpdateRequest;
import de.svws_nrw.edugate.control.tenant.TenantAccess;
import de.svws_nrw.edugate.core.domain.SchemaSource;
import de.svws_nrw.edugate.core.domain.SchemaStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.postgresql.util.PSQLException;

/**
 * Fachliche Operationen für Schemata/Schuldatenbanken einer Schule (ADR-012). Schema bleibt
 * tenant-gebunden (anders als {@code svws_instanz}, ADR-011); alle Operationen laufen daher über
 * {@link TenantAccess} statt {@code OperatorAccess} (ADR-009: "Alles unterhalb der
 * Mandanten-Wurzel ... wird im Normalfall tenant-gebunden über edugate_control bearbeitet").
 *
 * <p>Abweichend vom sonstigen {@code TenantAccess}-Muster (kein Audit, siehe
 * {@link TenantAccess}) verlangt ADR-012 explizit ein Audit-Log für sicherheitsrelevante
 * Schema-Operationen (Anlegen, Zuordnen, Deaktivieren/Reaktivieren). Diese Klasse schreibt den
 * {@code audit_admin}-Eintrag deshalb selbst, atomar in derselben Tenant-Transaktion (die Rolle
 * {@code edugate_control} hat dafür bereits seit V1 {@code INSERT}/{@code SELECT} auf
 * {@code audit_admin}). Anders als bei {@code OperatorAccess} gibt es hier <b>keinen</b>
 * unabhängigen zweiten Audit-Pfad für fehlgeschlagene Operationen (kein Rollback-Overlebender
 * ERROR/DENIED-Eintrag) - das ist ein bewusst offener Ausbaupunkt, siehe ADR-012 "gefährliche
 * Operationen" und die Aufgabenbeschreibung dieses Auftrags.
 */
@ApplicationScoped
public class SchemaService {

    private static final String POSTGRES_UNIQUE_VIOLATION = "23505";
    private static final String POSTGRES_FOREIGN_KEY_VIOLATION = "23503";
    private static final String SCHEMA_NAME_UNIQUE_CONSTRAINT = "schema_instanz_id_schema_name_unique";
    private static final String PRODUKTIV_UNIQUE_CONSTRAINT = "schema_schule_id_aktives_produktiv_unique";

    private static final String SELECT_COLUMNS =
        "id, schule_id, instanz_id, schema_name, umgebung, status, aktiv, beschreibung, source, "
            + "last_synced_at, created_at, updated_at";

    private static final String INSERT_AUDIT = """
        INSERT INTO audit_admin (admin_subject, action, entity_type, entity_id, tenant_id, outcome, details)
        VALUES (?, ?, 'schema', ?, ?, 'SUCCESS', ?::jsonb)
        """;

    @Inject
    TenantAccess tenantAccess;

    @Inject
    SchemaNamingService namingService;

    public List<SchemaDto> list(final UUID schultraegerId, final UUID schuleId) {
        return tenantAccess.execute(schultraegerId, connection -> {
            requireSchuleInTenant(connection, schuleId);
            final List<SchemaDto> items = new ArrayList<>();
            final String sql = "SELECT " + SELECT_COLUMNS + " FROM schema WHERE schule_id = ? ORDER BY umgebung, schema_name";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, schuleId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        items.add(toDto(resultSet));
                    }
                }
            }
            return items;
        });
    }

    public SchemaNamingSuggestionDto namingSuggestion(final UUID schultraegerId, final UUID schuleId, final String umgebung) {
        return tenantAccess.execute(schultraegerId, connection -> {
            final String schulnummer = requireSchuleInTenant(connection, schuleId);
            final Optional<String> vorschlag = namingService.vorschlagen(schulnummer, umgebung);
            return new SchemaNamingSuggestionDto(vorschlag.orElse(null), namingService.istBelastbareSchulnummer(schulnummer));
        });
    }

    public SchemaDto create(final String adminSubject, final UUID schultraegerId, final UUID schuleId,
            final SchemaCreateRequest request) {
        return tenantAccess.execute(schultraegerId, connection -> {
            requireSchuleInTenant(connection, schuleId);
            final String umgebung = namingService.normalisieren(request.umgebung());
            final String sql = "INSERT INTO schema (tenant_id, schule_id, instanz_id, schema_name, umgebung, beschreibung, source) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING " + SELECT_COLUMNS;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, schultraegerId);
                statement.setObject(2, schuleId);
                statement.setObject(3, request.instanzId());
                statement.setString(4, request.schemaName());
                statement.setString(5, umgebung);
                statement.setString(6, request.beschreibung());
                statement.setString(7, SchemaSource.MANUELL.name());
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    final SchemaDto dto = toDto(resultSet);
                    writeAudit(connection, adminSubject, AuditAction.SCHEMA_CREATE, dto.id(), schultraegerId,
                        "{\"schemaName\":\"" + escapeJson(dto.schemaName()) + "\"}");
                    return dto;
                }
            } catch (final SQLException e) {
                throw conflictOrNotFoundFor(e, request.schemaName());
            }
        });
    }

    public SchemaDto update(final String adminSubject, final UUID schultraegerId, final UUID schuleId, final UUID id,
            final SchemaUpdateRequest request) {
        return tenantAccess.execute(schultraegerId, connection -> {
            final String umgebung = namingService.normalisieren(request.umgebung());
            final String sql = "UPDATE schema SET instanz_id = ?, schema_name = ?, umgebung = ?, beschreibung = ?, "
                + "status = ?, aktiv = ?, updated_at = now() "
                + "WHERE id = ? AND schule_id = ? AND tenant_id = ? RETURNING " + SELECT_COLUMNS;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, request.instanzId());
                statement.setString(2, request.schemaName());
                statement.setString(3, umgebung);
                statement.setString(4, request.beschreibung());
                statement.setString(5, request.status().name());
                statement.setBoolean(6, request.aktiv());
                statement.setObject(7, id);
                statement.setObject(8, schuleId);
                statement.setObject(9, schultraegerId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new OperatorNotFoundException("Schema '" + id + "' wurde nicht gefunden.");
                    }
                    final SchemaDto dto = toDto(resultSet);
                    writeAudit(connection, adminSubject, AuditAction.SCHEMA_UPDATE, id, schultraegerId,
                        "{\"schemaName\":\"" + escapeJson(dto.schemaName()) + "\"}");
                    return dto;
                }
            } catch (final SQLException e) {
                throw conflictOrNotFoundFor(e, request.schemaName());
            }
        });
    }

    /**
     * Deaktiviert ein Schema (ADR-012: "gefährliche Operation", benötigt später explizite
     * Bestätigung/Schutzmechanismen - hier bereits als eigener, auditierter Endpunkt statt
     * beiläufiger Listenaktion umgesetzt). Physisches Löschen ist bewusst nicht Teil dieses
     * Auftrags.
     */
    public SchemaDto deactivate(final String adminSubject, final UUID schultraegerId, final UUID schuleId, final UUID id) {
        return tenantAccess.execute(schultraegerId, connection -> {
            final String sql = "UPDATE schema SET aktiv = false, status = ?, updated_at = now() "
                + "WHERE id = ? AND schule_id = ? AND tenant_id = ? RETURNING " + SELECT_COLUMNS;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, SchemaStatus.DEAKTIVIERT.name());
                statement.setObject(2, id);
                statement.setObject(3, schuleId);
                statement.setObject(4, schultraegerId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new OperatorNotFoundException("Schema '" + id + "' wurde nicht gefunden.");
                    }
                    final SchemaDto dto = toDto(resultSet);
                    writeAudit(connection, adminSubject, AuditAction.SCHEMA_DEACTIVATE, id, schultraegerId, null);
                    return dto;
                }
            }
        });
    }

    private String requireSchuleInTenant(final Connection connection, final UUID schuleId) throws SQLException {
        final String sql = "SELECT schulnummer FROM schule WHERE id = ? AND tenant_id = current_setting('edugate.tenant_id', true)::uuid";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, schuleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new OperatorNotFoundException("Schule '" + schuleId + "' wurde nicht gefunden.");
                }
                return resultSet.getString("schulnummer");
            }
        }
    }

    private void writeAudit(final Connection connection, final String adminSubject, final AuditAction action,
            final UUID entityId, final UUID tenantId, final String detailsJson) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_AUDIT)) {
            statement.setString(1, adminSubject);
            statement.setString(2, action.name());
            statement.setObject(3, entityId);
            statement.setObject(4, tenantId);
            statement.setString(5, detailsJson);
            statement.executeUpdate();
        }
    }

    private RuntimeException conflictOrNotFoundFor(final SQLException e, final String schemaName) {
        if (POSTGRES_FOREIGN_KEY_VIOLATION.equals(e.getSQLState())) {
            return new OperatorNotFoundException("Die referenzierte SVWS-Instanz wurde nicht gefunden.");
        }
        if (!POSTGRES_UNIQUE_VIOLATION.equals(e.getSQLState())) {
            return new RuntimeException("Schema-Operation fehlgeschlagen.", e);
        }
        final String constraint = constraintName(e);
        if (SCHEMA_NAME_UNIQUE_CONSTRAINT.equals(constraint)) {
            return new OperatorConflictException(
                "Ein Schema mit dem Namen '" + schemaName + "' existiert bereits auf dieser SVWS-Instanz.");
        }
        if (PRODUKTIV_UNIQUE_CONSTRAINT.equals(constraint)) {
            return new OperatorConflictException(
                "Diese Schule hat bereits ein aktives Produktiv-Schema. Es ist höchstens eines je Schule erlaubt.");
        }
        return new OperatorConflictException("Ein Schema mit diesen Werten existiert bereits.");
    }

    private String constraintName(final SQLException e) {
        if (e instanceof PSQLException psqlException && psqlException.getServerErrorMessage() != null) {
            return psqlException.getServerErrorMessage().getConstraint();
        }
        return null;
    }

    private String escapeJson(final String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private SchemaDto toDto(final ResultSet resultSet) throws SQLException {
        return new SchemaDto(
            (UUID) resultSet.getObject("id"),
            (UUID) resultSet.getObject("schule_id"),
            (UUID) resultSet.getObject("instanz_id"),
            resultSet.getString("schema_name"),
            resultSet.getString("umgebung"),
            SchemaStatus.valueOf(resultSet.getString("status")),
            resultSet.getBoolean("aktiv"),
            resultSet.getString("beschreibung"),
            SchemaSource.valueOf(resultSet.getString("source")),
            toInstant(resultSet.getTimestamp("last_synced_at")),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getTimestamp("updated_at").toInstant());
    }

    private Instant toInstant(final Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
