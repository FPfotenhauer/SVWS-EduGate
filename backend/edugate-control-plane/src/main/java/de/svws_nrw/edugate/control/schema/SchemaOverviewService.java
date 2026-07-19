package de.svws_nrw.edugate.control.schema;

import de.svws_nrw.edugate.control.operator.AuditAction;
import de.svws_nrw.edugate.control.operator.OperatorAccess;
import de.svws_nrw.edugate.control.operator.OperatorOutcome;
import de.svws_nrw.edugate.control.schema.dto.SchemaOverviewDto;
import de.svws_nrw.edugate.control.schema.dto.SchemaOverviewPageDto;
import de.svws_nrw.edugate.core.domain.InstanzStatus;
import de.svws_nrw.edugate.core.domain.SchemaSource;
import de.svws_nrw.edugate.core.domain.SchemaStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mandantenübergreifende Betreiber-Übersicht über Schuldatenbanken (ADR-013): verdichtet
 * {@code schema} mit {@code schule}, {@code schultraeger} und {@code svws_instanz} für die
 * Instanz- und die fachliche Schuldatenbank-Sicht. Nur lesend; läuft über
 * {@link OperatorAccess}, weil eine solche Übersicht per Definition mandantenübergreifend ist
 * (ADR-009, Use-Case "Mandantenübergreifende Listen, Suchen und Reports des
 * Dienstleister-Admins") - anders als die tenant-gebundene Einzelverwaltung eines Schemas
 * ({@link SchemaService}), die weiterhin über {@code TenantAccess}/{@code edugate_control} läuft.
 */
@ApplicationScoped
public class SchemaOverviewService {

    private static final String SELECT_COLUMNS = """
        s.id, s.schema_name, s.umgebung, s.status, s.aktiv, s.beschreibung, s.source, s.last_synced_at,
        s.created_at, s.updated_at,
        sc.id AS schule_id, sc.schulnummer, sc.name AS schule_name,
        st.id AS schultraeger_id, st.name AS schultraeger_name,
        si.id AS instanz_id, si.name AS instanz_name, si.base_url AS instanz_base_url, si.status AS instanz_status
        """;

    private static final String FROM_JOIN = """
        FROM schema s
        JOIN schule sc ON sc.id = s.schule_id
        JOIN schultraeger st ON st.id = s.tenant_id
        JOIN svws_instanz si ON si.id = s.instanz_id
        """;

    private static final String WHERE_FILTER = """
        WHERE (?::uuid IS NULL OR s.instanz_id = ?::uuid)
          AND (?::uuid IS NULL OR s.tenant_id = ?::uuid)
          AND (?::text IS NULL OR s.umgebung = ?::text)
          AND (?::text IS NULL OR s.status = ?::text)
          AND (?::text IS NULL OR s.schema_name ILIKE '%' || ?::text || '%'
                OR sc.name ILIKE '%' || ?::text || '%' OR sc.schulnummer ILIKE '%' || ?::text || '%'
                OR st.name ILIKE '%' || ?::text || '%' OR si.name ILIKE '%' || ?::text || '%')
        """;

    @Inject
    OperatorAccess operatorAccess;

    public SchemaOverviewPageDto list(final String adminSubject, final int page, final int size,
            final UUID instanzId, final UUID schultraegerId, final String umgebung, final SchemaStatus status,
            final String query) {
        return operatorAccess.execute(adminSubject, AuditAction.SCHEMA_OVERVIEW, "schema_overview", connection -> {
            final String umgebungFilter = normalisierterFilter(umgebung);
            final String statusFilter = status == null ? null : status.name();
            final String queryFilter = normalisierterFilter(query);

            final List<SchemaOverviewDto> items = new ArrayList<>();
            final String selectSql = "SELECT " + SELECT_COLUMNS + " " + FROM_JOIN + WHERE_FILTER
                + "ORDER BY st.name, sc.name, s.umgebung, s.schema_name LIMIT ? OFFSET ?";
            try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
                bindFilters(statement, instanzId, schultraegerId, umgebungFilter, statusFilter, queryFilter);
                statement.setInt(15, size);
                statement.setInt(16, page * size);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        items.add(toDto(resultSet));
                    }
                }
            }

            final String countSql = "SELECT count(*) " + FROM_JOIN + WHERE_FILTER;
            long total;
            try (PreparedStatement statement = connection.prepareStatement(countSql)) {
                bindFilters(statement, instanzId, schultraegerId, umgebungFilter, statusFilter, queryFilter);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    total = resultSet.getLong(1);
                }
            }

            final SchemaOverviewPageDto result = new SchemaOverviewPageDto(items, page, size, total);
            return OperatorOutcome.crossTenant(result, null);
        });
    }

    private void bindFilters(final PreparedStatement statement, final UUID instanzId, final UUID schultraegerId,
            final String umgebungFilter, final String statusFilter, final String queryFilter) throws SQLException {
        statement.setObject(1, instanzId);
        statement.setObject(2, instanzId);
        statement.setObject(3, schultraegerId);
        statement.setObject(4, schultraegerId);
        statement.setString(5, umgebungFilter);
        statement.setString(6, umgebungFilter);
        statement.setString(7, statusFilter);
        statement.setString(8, statusFilter);
        statement.setString(9, queryFilter);
        statement.setString(10, queryFilter);
        statement.setString(11, queryFilter);
        statement.setString(12, queryFilter);
        statement.setString(13, queryFilter);
        statement.setString(14, queryFilter);
    }

    private String normalisierterFilter(final String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private SchemaOverviewDto toDto(final ResultSet resultSet) throws SQLException {
        return new SchemaOverviewDto(
            (UUID) resultSet.getObject("id"),
            resultSet.getString("schema_name"),
            resultSet.getString("umgebung"),
            SchemaStatus.valueOf(resultSet.getString("status")),
            resultSet.getBoolean("aktiv"),
            resultSet.getString("beschreibung"),
            SchemaSource.valueOf(resultSet.getString("source")),
            toInstant(resultSet.getTimestamp("last_synced_at")),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getTimestamp("updated_at").toInstant(),
            (UUID) resultSet.getObject("schule_id"),
            resultSet.getString("schulnummer"),
            resultSet.getString("schule_name"),
            (UUID) resultSet.getObject("schultraeger_id"),
            resultSet.getString("schultraeger_name"),
            (UUID) resultSet.getObject("instanz_id"),
            resultSet.getString("instanz_name"),
            resultSet.getString("instanz_base_url"),
            InstanzStatus.valueOf(resultSet.getString("instanz_status")));
    }

    private Instant toInstant(final Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
