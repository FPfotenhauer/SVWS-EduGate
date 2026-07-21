package de.svws_nrw.edugate.control.svwsinstanz;

import de.svws_nrw.edugate.control.operator.AuditAction;
import de.svws_nrw.edugate.control.operator.OperatorAccess;
import de.svws_nrw.edugate.control.operator.OperatorConflictException;
import de.svws_nrw.edugate.control.operator.OperatorNotFoundException;
import de.svws_nrw.edugate.control.operator.OperatorOutcome;
import de.svws_nrw.edugate.control.schema.SchemaNamingService;
import de.svws_nrw.edugate.control.svwsinstanz.dto.SchemaFundZuordnungRequest;
import de.svws_nrw.edugate.control.svwsinstanz.dto.SvwsSchemaFundDto;
import de.svws_nrw.edugate.control.svwsinstanz.dto.SvwsSchemaSyncResultDto;
import de.svws_nrw.edugate.control.svwsinstanz.dto.SvwsSchulInfoDto;
import de.svws_nrw.edugate.control.svwsinstanz.dto.SvwsSchulInfoResultDto;
import de.svws_nrw.edugate.core.domain.SchemaSource;
import de.svws_nrw.edugate.core.domain.SchemaStatus;
import de.svws_nrw.edugate.core.secret.SecretStore;
import de.svws_nrw.edugate.core.svws.SvwsPrivilegedApiClient;
import de.svws_nrw.edugate.core.svws.SvwsSchemaListResult;
import de.svws_nrw.edugate.core.svws.SvwsSchemaListeEintrag;
import de.svws_nrw.edugate.core.svws.SvwsSchulInfo;
import de.svws_nrw.edugate.core.svws.SvwsSchulInfoResult;
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
import org.postgresql.util.PSQLException;

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

    private static final String POSTGRES_UNIQUE_VIOLATION = "23505";
    private static final String SCHEMA_NAME_UNIQUE_CONSTRAINT = "schema_instanz_id_schema_name_unique";
    private static final String PRODUKTIV_UNIQUE_CONSTRAINT = "schema_schule_id_aktives_produktiv_unique";

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

    private static final String SELECT_FUNDE_BASE = """
        SELECT f.id, f.instanz_id, f.schema_name, f.username, f.is_svws, f.revision, f.is_tainted,
               f.is_in_config, f.is_deactivated, f.first_seen_at, f.last_seen_at,
               s.id AS schema_id, s.aktiv AS schema_aktiv, s.status AS schema_status,
               s.schule_id, s.tenant_id AS schultraeger_id
        FROM svws_schema_fund f
        LEFT JOIN schema s ON s.instanz_id = f.instanz_id AND s.schema_name = f.schema_name
        WHERE f.instanz_id = ?
        """;

    private static final String SELECT_FUNDE = SELECT_FUNDE_BASE + " ORDER BY f.schema_name";
    private static final String SELECT_FUND_DTO_BY_ID = SELECT_FUNDE_BASE + " AND f.id = ?";

    private static final String SELECT_FUND_ROW_BY_ID =
        "SELECT id, schema_name, is_deactivated, is_tainted, last_seen_at FROM svws_schema_fund "
            + "WHERE instanz_id = ? AND id = ?";
    private static final String SELECT_FUND_ROW_BY_NAME =
        "SELECT id, schema_name, is_deactivated, is_tainted, last_seen_at FROM svws_schema_fund "
            + "WHERE instanz_id = ? AND schema_name = ?";

    private static final String SELECT_EXISTING_SCHEMA = """
        SELECT s.id, s.schule_id, sc.name AS schule_name
        FROM schema s JOIN schule sc ON sc.id = s.schule_id
        WHERE s.instanz_id = ? AND s.schema_name = ?
        """;

    private static final String INSERT_SCHEMA = """
        INSERT INTO schema (tenant_id, schule_id, instanz_id, schema_name, umgebung, beschreibung, status, aktiv,
                             source, last_synced_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        RETURNING id
        """;

    private static final String UPDATE_SCHEMA = """
        UPDATE schema SET tenant_id = ?, schule_id = ?, umgebung = ?, beschreibung = ?, status = ?, aktiv = ?,
                           source = ?, last_synced_at = ?, updated_at = now()
        WHERE id = ?
        RETURNING id
        """;

    @Inject
    OperatorAccess operatorAccess;

    @Inject
    SecretStore secretStore;

    @Inject
    SvwsPrivilegedApiClient privilegedApiClient;

    @Inject
    SchemaNamingService namingService;

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
                final InstanzRow instanz = loadInstanz(connection, instanzId);
                if (instanz.credentialsEncrypted() == null) {
                    return outcomeFor(instanzId, false,
                        "Keine Zugangsdaten hinterlegt - der Schema-Sync benötigt privilegierte Zugangsdaten.", 0);
                }

                final Credentials credentials = decryptCredentials(instanz.credentialsEncrypted());
                final SvwsSchemaListResult apiResult =
                    privilegedApiClient.listSchemas(instanz.baseUrl(), credentials.username(), credentials.password());
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
     * Ordnet einen unzugeordneten SVWS-Schema-Fund einer Schule zu (ADR-014 Schritt 2, ADR-012
     * "Unzugeordnete und importierte Schemata"): Der Betreiber muss Schulträger, Schule und
     * Umgebung bewusst auswählen - EduGate rät nie automatisch aus dem Schemanamen. Legt einen
     * neuen {@code schema}-Datensatz an oder aktualisiert einen vorhandenen (derselben Schule),
     * damit ein Korrigieren von Umgebung/Beschreibung ohne Fehler möglich bleibt. Ist das Schema
     * bereits einer <b>anderen</b> Schule zugeordnet, wird das als Konflikt abgelehnt.
     */
    public SvwsSchemaFundDto assign(final String adminSubject, final UUID instanzId, final UUID fundId,
            final SchemaFundZuordnungRequest request) {
        return assignInternal(adminSubject, instanzId, fundId, null, request);
    }

    /** Wie {@link #assign(String, UUID, UUID, SchemaFundZuordnungRequest)}, aber Fund-Lookup über den Schemanamen. */
    public SvwsSchemaFundDto assignBySchemaName(final String adminSubject, final UUID instanzId, final String schemaName,
            final SchemaFundZuordnungRequest request) {
        return assignInternal(adminSubject, instanzId, null, schemaName, request);
    }

    private SvwsSchemaFundDto assignInternal(final String adminSubject, final UUID instanzId, final UUID fundId,
            final String schemaName, final SchemaFundZuordnungRequest request) {
        return operatorAccess.execute(adminSubject, AuditAction.SVWS_SCHEMA_FUND_ZUORDNEN, "svws_schema_fund",
            connection -> {
                final FundRow fund = fundId != null
                    ? selectFundRow(connection, instanzId, fundId)
                    : selectFundRow(connection, instanzId, schemaName);

                requireSchultraegerExists(connection, request.schultraegerId());
                requireSchuleInSchultraeger(connection, request.schultraegerId(), request.schuleId());

                final String umgebung = namingService.normalisieren(request.umgebung());
                final SchemaStatus status = statusFor(fund);
                final boolean aktiv = status != SchemaStatus.DEAKTIVIERT;
                final Timestamp lastSynced = fund.lastSeenAt() == null ? null : Timestamp.from(fund.lastSeenAt());

                final ExistingSchemaRow existing = selectExistingSchema(connection, instanzId, fund.schemaName());
                final UUID schemaId;
                if (existing != null) {
                    if (!existing.schuleId().equals(request.schuleId())) {
                        throw new OperatorConflictException("Das SVWS-Schema '" + fund.schemaName()
                            + "' ist bereits der Schule '" + existing.schuleName() + "' zugeordnet.");
                    }
                    schemaId = updateSchema(connection, existing.id(), request, umgebung, status, aktiv, lastSynced);
                } else {
                    schemaId =
                        insertSchema(connection, request, instanzId, fund.schemaName(), umgebung, status, aktiv, lastSynced);
                }

                final SvwsSchemaFundDto dto = selectFundDtoById(connection, instanzId, fund.id());
                final String detailsJson = "{\"schemaName\":\"" + escapeJson(fund.schemaName()) + "\",\"umgebung\":\""
                    + escapeJson(umgebung) + "\"}";
                return new OperatorOutcome<>(dto, schemaId, request.schultraegerId(), detailsJson);
            });
    }

    /**
     * Fragt optional die im SVWS-Schema hinterlegten Schulinformationen ab (ADR-014 Schritt 2:
     * "Wenn der Aufruf fehlschlägt oder keine Informationen liefert, darf die Zuordnung trotzdem
     * manuell möglich bleiben"). Liefert bei jedem fachlichen Fehlschlag (fehlende Zugangsdaten,
     * SVWS-seitiger Fehler) ein {@code success=false}-Ergebnis statt einer Exception - nur ein
     * unbekannter Fund/Instanz ist ein echter Fehler.
     */
    public SvwsSchulInfoResultDto schulInfo(final String adminSubject, final UUID instanzId, final UUID fundId) {
        return operatorAccess.execute(adminSubject, AuditAction.SVWS_SCHEMA_FUND_SCHULINFO, "svws_schema_fund",
            connection -> {
                final FundRow fund = selectFundRow(connection, instanzId, fundId);
                final InstanzRow instanz = loadInstanz(connection, instanzId);

                if (instanz.credentialsEncrypted() == null) {
                    return schulInfoOutcome(instanzId,
                        SvwsSchulInfoResult.failure("Keine Zugangsdaten hinterlegt - SchulInfo-Abruf nicht möglich."));
                }

                final Credentials credentials = decryptCredentials(instanz.credentialsEncrypted());
                final SvwsSchulInfoResult apiResult = privilegedApiClient.getSchulInfo(
                    instanz.baseUrl(), credentials.username(), credentials.password(), fund.schemaName());
                return schulInfoOutcome(instanzId, apiResult);
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

    private record InstanzRow(String baseUrl, byte[] credentialsEncrypted) {
    }

    private record Credentials(String username, String password) {
    }

    private record FundRow(UUID id, String schemaName, Boolean isDeactivated, Boolean isTainted, Instant lastSeenAt) {
    }

    private record ExistingSchemaRow(UUID id, UUID schuleId, String schuleName) {
    }

    private InstanzRow loadInstanz(final Connection connection, final UUID instanzId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_INSTANZ)) {
            statement.setObject(1, instanzId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new OperatorNotFoundException("SVWS-Instanz '" + instanzId + "' wurde nicht gefunden.");
                }
                return new InstanzRow(resultSet.getString("base_url"), resultSet.getBytes("credentials_encrypted"));
            }
        }
    }

    private Credentials decryptCredentials(final byte[] encrypted) {
        final String decrypted = new String(secretStore.decrypt(encrypted), StandardCharsets.UTF_8);
        final int separator = decrypted.indexOf(':');
        final String username = separator < 0 ? decrypted : decrypted.substring(0, separator);
        final String password = separator < 0 ? "" : decrypted.substring(separator + 1);
        return new Credentials(username, password);
    }

    private FundRow selectFundRow(final Connection connection, final UUID instanzId, final UUID fundId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_FUND_ROW_BY_ID)) {
            statement.setObject(1, instanzId);
            statement.setObject(2, fundId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new OperatorNotFoundException("SVWS-Schema-Fund '" + fundId + "' wurde nicht gefunden.");
                }
                return toFundRow(resultSet);
            }
        }
    }

    private FundRow selectFundRow(final Connection connection, final UUID instanzId, final String schemaName)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_FUND_ROW_BY_NAME)) {
            statement.setObject(1, instanzId);
            statement.setString(2, schemaName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new OperatorNotFoundException(
                        "SVWS-Schema-Fund '" + schemaName + "' wurde auf dieser Instanz nicht gefunden.");
                }
                return toFundRow(resultSet);
            }
        }
    }

    private FundRow toFundRow(final ResultSet resultSet) throws SQLException {
        return new FundRow(
            (UUID) resultSet.getObject("id"),
            resultSet.getString("schema_name"),
            (Boolean) resultSet.getObject("is_deactivated"),
            (Boolean) resultSet.getObject("is_tainted"),
            toInstant(resultSet.getTimestamp("last_seen_at")));
    }

    private void requireSchultraegerExists(final Connection connection, final UUID schultraegerId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM schultraeger WHERE id = ?")) {
            statement.setObject(1, schultraegerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new OperatorNotFoundException("Schulträger '" + schultraegerId + "' wurde nicht gefunden.");
                }
            }
        }
    }

    private void requireSchuleInSchultraeger(final Connection connection, final UUID schultraegerId, final UUID schuleId)
            throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT 1 FROM schule WHERE id = ? AND tenant_id = ?")) {
            statement.setObject(1, schuleId);
            statement.setObject(2, schultraegerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new OperatorNotFoundException(
                        "Schule '" + schuleId + "' wurde für den angegebenen Schulträger nicht gefunden.");
                }
            }
        }
    }

    private ExistingSchemaRow selectExistingSchema(final Connection connection, final UUID instanzId,
            final String schemaName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_EXISTING_SCHEMA)) {
            statement.setObject(1, instanzId);
            statement.setString(2, schemaName);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new ExistingSchemaRow(
                    (UUID) resultSet.getObject("id"),
                    (UUID) resultSet.getObject("schule_id"),
                    resultSet.getString("schule_name"));
            }
        }
    }

    /**
     * Leitet den technischen Status aus den Fund-Flags ab (ADR-014 Schritt 2: "Status passend
     * setzen, z. B. VORHANDEN, bei isDeactivated=true eher DEAKTIVIERT, bei isTainted=true
     * FEHLER"). Deaktiviert hat Vorrang vor tainted, weil es der definitivere SVWS-seitige
     * Zustand ist.
     */
    private SchemaStatus statusFor(final FundRow fund) {
        if (Boolean.TRUE.equals(fund.isDeactivated())) {
            return SchemaStatus.DEAKTIVIERT;
        }
        if (Boolean.TRUE.equals(fund.isTainted())) {
            return SchemaStatus.FEHLER;
        }
        return SchemaStatus.VORHANDEN;
    }

    private UUID insertSchema(final Connection connection, final SchemaFundZuordnungRequest request,
            final UUID instanzId, final String schemaName, final String umgebung, final SchemaStatus status,
            final boolean aktiv, final Timestamp lastSynced) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SCHEMA)) {
            statement.setObject(1, request.schultraegerId());
            statement.setObject(2, request.schuleId());
            statement.setObject(3, instanzId);
            statement.setString(4, schemaName);
            statement.setString(5, umgebung);
            statement.setString(6, request.beschreibung());
            statement.setString(7, status.name());
            statement.setBoolean(8, aktiv);
            statement.setString(9, SchemaSource.SYNCHRONISIERT.name());
            statement.setTimestamp(10, lastSynced);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return (UUID) resultSet.getObject("id");
            }
        } catch (final SQLException e) {
            throw conflictFor(e, schemaName);
        }
    }

    private UUID updateSchema(final Connection connection, final UUID schemaId, final SchemaFundZuordnungRequest request,
            final String umgebung, final SchemaStatus status, final boolean aktiv, final Timestamp lastSynced)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_SCHEMA)) {
            statement.setObject(1, request.schultraegerId());
            statement.setObject(2, request.schuleId());
            statement.setString(3, umgebung);
            statement.setString(4, request.beschreibung());
            statement.setString(5, status.name());
            statement.setBoolean(6, aktiv);
            statement.setString(7, SchemaSource.SYNCHRONISIERT.name());
            statement.setTimestamp(8, lastSynced);
            statement.setObject(9, schemaId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return (UUID) resultSet.getObject("id");
            }
        } catch (final SQLException e) {
            throw conflictFor(e, null);
        }
    }

    private SvwsSchemaFundDto selectFundDtoById(final Connection connection, final UUID instanzId, final UUID fundId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_FUND_DTO_BY_ID)) {
            statement.setObject(1, instanzId);
            statement.setObject(2, fundId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return toDto(resultSet);
            }
        }
    }

    private OperatorOutcome<SvwsSchulInfoResultDto> schulInfoOutcome(final UUID instanzId,
            final SvwsSchulInfoResult apiResult) {
        final SvwsSchulInfo info = apiResult.schulInfo();
        final SvwsSchulInfoDto dto = info == null ? null : new SvwsSchulInfoDto(info.schulnummer(), info.schulform(),
            info.bezeichnung(), info.strassenname(), info.hausnummer(), info.hausnummerZusatz(), info.plz(), info.ort());
        final SvwsSchulInfoResultDto resultDto = new SvwsSchulInfoResultDto(apiResult.success(), apiResult.message(), dto);
        final String detailsJson = "{\"success\":" + apiResult.success() + "}";
        return new OperatorOutcome<>(resultDto, instanzId, null, detailsJson);
    }

    private RuntimeException conflictFor(final SQLException e, final String schemaName) {
        if (!POSTGRES_UNIQUE_VIOLATION.equals(e.getSQLState())) {
            return new RuntimeException("Schema-Zuordnung fehlgeschlagen.", e);
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
