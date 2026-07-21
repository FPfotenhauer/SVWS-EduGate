package de.svws_nrw.edugate.control.svwsinstanz;

import de.svws_nrw.edugate.control.operator.AuditAction;
import de.svws_nrw.edugate.control.operator.OperatorAccess;
import de.svws_nrw.edugate.control.operator.OperatorNotFoundException;
import de.svws_nrw.edugate.control.operator.OperatorOutcome;
import de.svws_nrw.edugate.control.svwsinstanz.dto.SvwsSchemaFundDto;
import de.svws_nrw.edugate.control.svwsinstanz.dto.SvwsSchemaSyncResultDto;
import de.svws_nrw.edugate.core.secret.SecretStore;
import de.svws_nrw.edugate.core.svws.SvwsPrivilegedApiClient;
import de.svws_nrw.edugate.core.svws.SvwsSchemaListResult;
import de.svws_nrw.edugate.core.svws.SvwsSchemaListeEintrag;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Read-only Sync mit der SVWS-Privileged-API (ADR-014 Stufe 1: "EduGate liest vorhandene
 * Schemata je SVWS-Instanz und zeigt sie als bekannte oder unzugeordnete Funde an. Keine
 * automatische Tenant-Zuordnung allein aus dem Schemanamen"). Persistiert die Ergebnisse in
 * {@code svws_schema_fund}, damit die Instanzsicht (ADR-013) die gespeicherten Sync-Daten zeigen
 * kann, statt bei jedem Seitenaufruf live gegen SVWS zu sprechen (ADR-012).
 *
 * <p>{@code svws_schema_fund} ist wie {@code svws_instanz} eine mandantenübergreifende
 * Betriebsressource (ADR-011-Muster); alle Operationen laufen daher über {@link OperatorAccess}.
 */
@ApplicationScoped
public class SvwsSchemaFundService {

    private static final String SELECT_INSTANZ =
        "SELECT base_url, credentials_encrypted FROM svws_instanz WHERE id = ?";

    private static final String UPSERT_FUND = """
        INSERT INTO svws_schema_fund
            (instanz_id, schema_name, username, is_svws, revision, is_tainted, is_in_config, is_deactivated,
             first_seen_at, last_seen_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), now())
        ON CONFLICT (instanz_id, schema_name) DO UPDATE SET
            username = EXCLUDED.username,
            is_svws = EXCLUDED.is_svws,
            revision = EXCLUDED.revision,
            is_tainted = EXCLUDED.is_tainted,
            is_in_config = EXCLUDED.is_in_config,
            is_deactivated = EXCLUDED.is_deactivated,
            last_seen_at = now(),
            updated_at = now()
        """;

    private static final String DELETE_STALE_FUNDS =
        "DELETE FROM svws_schema_fund WHERE instanz_id = ? AND schema_name <> ALL (?)";

    private static final String SELECT_FUNDE = """
        SELECT f.id, f.instanz_id, f.schema_name, f.username, f.is_svws, f.revision, f.is_tainted,
               f.is_in_config, f.is_deactivated, f.first_seen_at, f.last_seen_at,
               s.id AS schema_id, s.aktiv AS schema_aktiv, s.status AS schema_status,
               s.schule_id, s.tenant_id AS schultraeger_id
        FROM svws_schema_fund f
        LEFT JOIN schema s ON s.instanz_id = f.instanz_id AND s.schema_name = f.schema_name
        WHERE f.instanz_id = ?
        ORDER BY f.schema_name
        """;

    @Inject
    OperatorAccess operatorAccess;

    @Inject
    SecretStore secretStore;

    @Inject
    SvwsPrivilegedApiClient privilegedApiClient;

    /**
     * Führt einen Sync-Lauf gegen die Privileged-API aus (ADR-006: Entschlüsselung nur
     * unmittelbar vor dem Aufruf, im Speicher) und ersetzt die gespeicherten Funde der Instanz
     * durch das aktuelle Ergebnis. Ein fachlicher Fehlschlag (keine Zugangsdaten, ungültige
     * Rechte, Netzwerkfehler) wirft keine Exception, sondern liefert ein
     * {@link SvwsSchemaSyncResultDto} mit {@code success=false} - der Sync-Versuch selbst gilt
     * als durchgeführt und wird auditiert.
     */
    public SvwsSchemaSyncResultDto sync(final String adminSubject, final UUID instanzId) {
        return operatorAccess.execute(adminSubject, AuditAction.SVWS_INSTANZ_SCHEMA_SYNC, "svws_schema_fund",
            connection -> {
                final String baseUrl;
                final byte[] credentialsEncrypted;
                try (PreparedStatement statement = connection.prepareStatement(SELECT_INSTANZ)) {
                    statement.setObject(1, instanzId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            throw new OperatorNotFoundException("SVWS-Instanz '" + instanzId + "' wurde nicht gefunden.");
                        }
                        baseUrl = resultSet.getString("base_url");
                        credentialsEncrypted = resultSet.getBytes("credentials_encrypted");
                    }
                }

                if (credentialsEncrypted == null) {
                    return outcomeFor(instanzId, false,
                        "Keine Zugangsdaten hinterlegt - der Schema-Sync benötigt privilegierte Zugangsdaten.", 0);
                }

                final String decrypted = new String(secretStore.decrypt(credentialsEncrypted), StandardCharsets.UTF_8);
                final int separator = decrypted.indexOf(':');
                final String username = separator < 0 ? decrypted : decrypted.substring(0, separator);
                final String password = separator < 0 ? "" : decrypted.substring(separator + 1);

                final SvwsSchemaListResult apiResult = privilegedApiClient.listSchemas(baseUrl, username, password);
                if (!apiResult.success()) {
                    return outcomeFor(instanzId, false, apiResult.message(), 0);
                }

                final Set<String> currentNames = new HashSet<>();
                for (final SvwsSchemaListeEintrag eintrag : apiResult.entries()) {
                    currentNames.add(eintrag.name());
                    upsertFund(connection, instanzId, eintrag);
                }
                deleteStaleFunds(connection, instanzId, currentNames);

                return outcomeFor(instanzId, true, apiResult.message(), apiResult.entries().size());
            });
    }

    /**
     * Liest die gespeicherten Funde einer Instanz (ADR-012: "Nutze vorhandene Sync-/Fund-Daten,
     * falls vorhanden, statt direkt im Frontend gegen SVWS zu sprechen") und klassifiziert sie
     * gegenüber dem EduGate-eigenen {@code schema}-Bestand.
     */
    public List<SvwsSchemaFundDto> list(final String adminSubject, final UUID instanzId) {
        return operatorAccess.execute(adminSubject, AuditAction.SVWS_INSTANZ_SCHEMA_FUNDE_LIST, "svws_schema_fund",
            connection -> {
                requireInstanzExists(connection, instanzId);
                final List<SvwsSchemaFundDto> items = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(SELECT_FUNDE)) {
                    statement.setObject(1, instanzId);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            items.add(toDto(resultSet));
                        }
                    }
                }
                // Anders als eine echte Cross-Tenant-Liste (OperatorOutcome.crossTenant) ist dies
                // die Ansicht genau einer Instanz - der Audit-Eintrag trägt daher instanzId als
                // entityId, analog zu SvwsInstanzService#get.
                return new OperatorOutcome<>(items, instanzId, null, null);
            });
    }

    private void requireInstanzExists(final Connection connection, final UUID instanzId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM svws_instanz WHERE id = ?")) {
            statement.setObject(1, instanzId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new OperatorNotFoundException("SVWS-Instanz '" + instanzId + "' wurde nicht gefunden.");
                }
            }
        }
    }

    private void upsertFund(final Connection connection, final UUID instanzId, final SvwsSchemaListeEintrag eintrag)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_FUND)) {
            statement.setObject(1, instanzId);
            statement.setString(2, eintrag.name());
            statement.setString(3, eintrag.username());
            setNullableBoolean(statement, 4, eintrag.isSvws());
            setNullableLong(statement, 5, eintrag.revision());
            setNullableBoolean(statement, 6, eintrag.isTainted());
            setNullableBoolean(statement, 7, eintrag.isInConfig());
            setNullableBoolean(statement, 8, eintrag.isDeactivated());
            statement.executeUpdate();
        }
    }

    private void deleteStaleFunds(final Connection connection, final UUID instanzId, final Set<String> currentNames)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DELETE_STALE_FUNDS)) {
            final Array namesArray = connection.createArrayOf("text", currentNames.toArray());
            statement.setObject(1, instanzId);
            statement.setArray(2, namesArray);
            statement.executeUpdate();
        }
    }

    private OperatorOutcome<SvwsSchemaSyncResultDto> outcomeFor(final UUID instanzId,
            final boolean success, final String message, final int count) {
        final Instant now = Instant.now();
        final SvwsSchemaSyncResultDto dto = new SvwsSchemaSyncResultDto(success, message, count, now);
        final String detailsJson = "{\"success\":" + success + ",\"gefundeneSchemata\":" + count + "}";
        return new OperatorOutcome<>(dto, instanzId, null, detailsJson);
    }

    private void setNullableBoolean(final PreparedStatement statement, final int index, final Boolean value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BOOLEAN);
        } else {
            statement.setBoolean(index, value);
        }
    }

    private void setNullableLong(final PreparedStatement statement, final int index, final Long value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    private SvwsSchemaFundDto toDto(final ResultSet resultSet) throws SQLException {
        final UUID schemaId = (UUID) resultSet.getObject("schema_id");
        SchemaFundZuordnungsStatus status = SchemaFundZuordnungsStatus.UNZUGEORDNET;
        UUID schuleId = null;
        UUID schultraegerId = null;
        if (schemaId != null) {
            final boolean schemaAktiv = resultSet.getBoolean("schema_aktiv");
            final String schemaStatus = resultSet.getString("schema_status");
            final Boolean isDeactivated = (Boolean) resultSet.getObject("is_deactivated");
            final boolean konflikt = (Boolean.TRUE.equals(isDeactivated) && schemaAktiv)
                || (Boolean.FALSE.equals(isDeactivated) && "DEAKTIVIERT".equals(schemaStatus));
            status = konflikt ? SchemaFundZuordnungsStatus.KONFLIKT : SchemaFundZuordnungsStatus.BEKANNT;
            schuleId = (UUID) resultSet.getObject("schule_id");
            schultraegerId = (UUID) resultSet.getObject("schultraeger_id");
        }

        return new SvwsSchemaFundDto(
            (UUID) resultSet.getObject("id"),
            (UUID) resultSet.getObject("instanz_id"),
            resultSet.getString("schema_name"),
            resultSet.getString("username"),
            (Boolean) resultSet.getObject("is_svws"),
            (Long) resultSet.getObject("revision"),
            (Boolean) resultSet.getObject("is_tainted"),
            (Boolean) resultSet.getObject("is_in_config"),
            (Boolean) resultSet.getObject("is_deactivated"),
            toInstant(resultSet.getTimestamp("first_seen_at")),
            toInstant(resultSet.getTimestamp("last_seen_at")),
            status,
            schemaId,
            schuleId,
            schultraegerId);
    }

    private Instant toInstant(final Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
