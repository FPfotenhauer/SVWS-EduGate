package de.svws_nrw.edugate.control.schule;

import de.svws_nrw.edugate.control.operator.OperatorNotFoundException;
import de.svws_nrw.edugate.control.schule.dto.SchuleCreateRequest;
import de.svws_nrw.edugate.control.schule.dto.SchuleDto;
import de.svws_nrw.edugate.control.schule.dto.SchuleUpdateRequest;
import de.svws_nrw.edugate.control.tenant.TenantAccess;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fachliche Operationen für Schulen eines Schulträgers. Schule ist eine tenant-gebundene
 * Kind-Entität "unterhalb der Mandanten-Wurzel" (ADR-009, dort explizit als Beispiel genannt),
 * daher laufen alle Operationen über {@link TenantAccess} statt {@code OperatorAccess} - analog
 * zu Ansprechpartner. Schule ist die notwendige Zuordnungsstufe für Schuldatenbanken/Schemata
 * (ADR-012: Schulträger -&gt; Schule -&gt; Schema -&gt; Instanz).
 */
@ApplicationScoped
public class SchuleService {

    private static final String SELECT_COLUMNS = "id, tenant_id, schulnummer, name, created_at, updated_at";

    @Inject
    TenantAccess tenantAccess;

    public List<SchuleDto> list(final UUID schultraegerId) {
        return tenantAccess.execute(schultraegerId, connection -> {
            final List<SchuleDto> items = new ArrayList<>();
            final String sql = "SELECT " + SELECT_COLUMNS + " FROM schule WHERE tenant_id = ? ORDER BY schulnummer";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, schultraegerId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        items.add(toDto(resultSet));
                    }
                }
            }
            return items;
        });
    }

    public SchuleDto get(final UUID schultraegerId, final UUID id) {
        return tenantAccess.execute(schultraegerId, connection -> selectById(connection, id));
    }

    public SchuleDto create(final UUID schultraegerId, final SchuleCreateRequest request) {
        return tenantAccess.execute(schultraegerId, connection -> {
            final String sql = "INSERT INTO schule (tenant_id, schulnummer, name) VALUES (?, ?, ?) "
                + "RETURNING " + SELECT_COLUMNS;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, schultraegerId);
                statement.setString(2, request.schulnummer());
                statement.setString(3, request.name());
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return toDto(resultSet);
                }
            }
        });
    }

    public SchuleDto update(final UUID schultraegerId, final UUID id, final SchuleUpdateRequest request) {
        return tenantAccess.execute(schultraegerId, connection -> {
            final String sql = "UPDATE schule SET schulnummer = ?, name = ?, updated_at = now() "
                + "WHERE id = ? AND tenant_id = ? RETURNING " + SELECT_COLUMNS;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, request.schulnummer());
                statement.setString(2, request.name());
                statement.setObject(3, id);
                statement.setObject(4, schultraegerId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new OperatorNotFoundException("Schule '" + id + "' wurde nicht gefunden.");
                    }
                    return toDto(resultSet);
                }
            }
        });
    }

    private SchuleDto selectById(final java.sql.Connection connection, final UUID id) throws SQLException {
        final String sql = "SELECT " + SELECT_COLUMNS + " FROM schule WHERE id = ? AND tenant_id = current_setting('edugate.tenant_id', true)::uuid";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new OperatorNotFoundException("Schule '" + id + "' wurde nicht gefunden.");
                }
                return toDto(resultSet);
            }
        }
    }

    private SchuleDto toDto(final ResultSet resultSet) throws SQLException {
        return new SchuleDto(
            (UUID) resultSet.getObject("id"),
            (UUID) resultSet.getObject("tenant_id"),
            resultSet.getString("schulnummer"),
            resultSet.getString("name"),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getTimestamp("updated_at").toInstant());
    }
}
