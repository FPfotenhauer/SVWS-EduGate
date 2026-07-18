package de.svws_nrw.edugate.control.ansprechpartner;

import de.svws_nrw.edugate.control.ansprechpartner.dto.AnsprechpartnerCreateRequest;
import de.svws_nrw.edugate.control.ansprechpartner.dto.AnsprechpartnerDto;
import de.svws_nrw.edugate.control.ansprechpartner.dto.AnsprechpartnerUpdateRequest;
import de.svws_nrw.edugate.control.operator.OperatorNotFoundException;
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
 * Fachliche Operationen für Ansprechpartner eines Schulträgers. Ansprechpartner ist eine
 * tenant-gebundene Kind-Entität "unterhalb der Mandanten-Wurzel" (ADR-009), daher laufen alle
 * Operationen über {@link TenantAccess} statt {@code OperatorAccess} - anders als bei
 * Schulträger selbst oder SVWS-Instanzen, die mandantenübergreifende Ressourcen sind.
 */
@ApplicationScoped
public class AnsprechpartnerService {

    private static final String SELECT_COLUMNS =
        "id, name, vorname, titel, abteilung, funktion, email, telefon_festnetz, telefon_mobil, "
            + "beschreibung, created_at, updated_at";

    @Inject
    TenantAccess tenantAccess;

    public List<AnsprechpartnerDto> list(final UUID schultraegerId) {
        return tenantAccess.execute(schultraegerId, connection -> {
            final List<AnsprechpartnerDto> items = new ArrayList<>();
            final String sql = "SELECT " + SELECT_COLUMNS + " FROM ansprechpartner "
                + "WHERE tenant_id = ? ORDER BY name, vorname";
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

    public AnsprechpartnerDto create(final UUID schultraegerId, final AnsprechpartnerCreateRequest request) {
        return tenantAccess.execute(schultraegerId, connection -> {
            final String sql = "INSERT INTO ansprechpartner "
                + "(tenant_id, name, vorname, titel, abteilung, funktion, email, telefon_festnetz, telefon_mobil, beschreibung) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING " + SELECT_COLUMNS;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, schultraegerId);
                statement.setString(2, request.name());
                statement.setString(3, request.vorname());
                statement.setString(4, request.titel());
                statement.setString(5, request.abteilung());
                statement.setString(6, request.funktion());
                statement.setString(7, request.email());
                statement.setString(8, request.telefonFestnetz());
                statement.setString(9, request.telefonMobil());
                statement.setString(10, request.beschreibung());
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    return toDto(resultSet);
                }
            }
        });
    }

    public AnsprechpartnerDto update(final UUID schultraegerId, final UUID id, final AnsprechpartnerUpdateRequest request) {
        return tenantAccess.execute(schultraegerId, connection -> {
            final String sql = "UPDATE ansprechpartner SET name = ?, vorname = ?, titel = ?, abteilung = ?, "
                + "funktion = ?, email = ?, telefon_festnetz = ?, telefon_mobil = ?, beschreibung = ?, updated_at = now() "
                + "WHERE id = ? AND tenant_id = ? RETURNING " + SELECT_COLUMNS;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, request.name());
                statement.setString(2, request.vorname());
                statement.setString(3, request.titel());
                statement.setString(4, request.abteilung());
                statement.setString(5, request.funktion());
                statement.setString(6, request.email());
                statement.setString(7, request.telefonFestnetz());
                statement.setString(8, request.telefonMobil());
                statement.setString(9, request.beschreibung());
                statement.setObject(10, id);
                statement.setObject(11, schultraegerId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new OperatorNotFoundException("Ansprechpartner '" + id + "' wurde nicht gefunden.");
                    }
                    return toDto(resultSet);
                }
            }
        });
    }

    public void delete(final UUID schultraegerId, final UUID id) {
        tenantAccess.execute(schultraegerId, connection -> {
            final String sql = "DELETE FROM ansprechpartner WHERE id = ? AND tenant_id = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, id);
                statement.setObject(2, schultraegerId);
                final int deleted = statement.executeUpdate();
                if (deleted == 0) {
                    throw new OperatorNotFoundException("Ansprechpartner '" + id + "' wurde nicht gefunden.");
                }
                return Boolean.TRUE;
            }
        });
    }

    private AnsprechpartnerDto toDto(final ResultSet resultSet) throws SQLException {
        return new AnsprechpartnerDto(
            (UUID) resultSet.getObject("id"),
            resultSet.getString("name"),
            resultSet.getString("vorname"),
            resultSet.getString("titel"),
            resultSet.getString("abteilung"),
            resultSet.getString("funktion"),
            resultSet.getString("email"),
            resultSet.getString("telefon_festnetz"),
            resultSet.getString("telefon_mobil"),
            resultSet.getString("beschreibung"),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getTimestamp("updated_at").toInstant());
    }
}
