package de.svws_nrw.edugate.control.schuldatei;

import de.svws_nrw.edugate.control.operator.AuditAction;
import de.svws_nrw.edugate.control.operator.OperatorAccess;
import de.svws_nrw.edugate.control.operator.OperatorOutcome;
import de.svws_nrw.edugate.control.schuldatei.dto.SchuldateiImportStatusDto;
import de.svws_nrw.edugate.control.schuldatei.dto.SchuleKatalogDto;
import de.svws_nrw.edugate.control.schuldatei.dto.SchuleKatalogPageDto;
import de.svws_nrw.edugate.control.schuldatei.dto.SchultraegerKatalogDto;
import de.svws_nrw.edugate.control.schuldatei.dto.SchultraegerKatalogPageDto;
import de.svws_nrw.edugate.core.schuldatei.NrwSchuldateiClient;
import de.svws_nrw.edugate.core.schuldatei.NrwSchuldateiResult;
import de.svws_nrw.edugate.core.schuldatei.NrwSchuleEintrag;
import de.svws_nrw.edugate.core.schuldatei.NrwSchultraegerEintrag;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Landes-Schuldatei als Referenzkatalog (ADR-020, Nachtrag "Grundlagenrecherche vor Umsetzung"):
 * ruft die amtliche NRW-Schuldatei ab, füllt {@code schule_katalog}/{@code schultraeger_katalog}
 * per Upsert (stabile Identität über Refreshs hinweg statt Clear-and-Insert) und liefert
 * durchsuchbare/filterbare Listen für die Einstellungen-Kachel "Schuldatei".
 *
 * <p>{@code schule_katalog}/{@code schultraeger_katalog}/{@code schuldatei_import} sind wie
 * {@code svws_instanz} mandantenübergreifende Betriebsressourcen (ADR-011-Muster); alle
 * Operationen laufen daher über {@link OperatorAccess}.
 */
@ApplicationScoped
public class SchuldateiService {

    private static final String BUNDESLANDKENNUNG = "NRW";
    private static final DateTimeFormatter NRW_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY);

    private static final String UPSERT_SCHULTRAEGER = """
        INSERT INTO schultraeger_katalog
            (bundeslandkennung, traegernummer, traegername, traegerschaftsart, strasse, plz, ort, aufloesung,
             last_seen_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())
        ON CONFLICT (bundeslandkennung, traegernummer) DO UPDATE SET
            traegername = EXCLUDED.traegername,
            traegerschaftsart = EXCLUDED.traegerschaftsart,
            strasse = EXCLUDED.strasse,
            plz = EXCLUDED.plz,
            ort = EXCLUDED.ort,
            aufloesung = EXCLUDED.aufloesung,
            last_seen_at = now(),
            updated_at = now()
        """;

    private static final String UPSERT_SCHULE = """
        INSERT INTO schule_katalog
            (bundeslandkennung, schulnummer, schulname, schultraegernummer, schulform, strasse, plz, ort, kreis,
             telefon, fax, email, homepage, aufloesung, last_seen_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
        ON CONFLICT (bundeslandkennung, schulnummer) DO UPDATE SET
            schulname = EXCLUDED.schulname,
            schultraegernummer = EXCLUDED.schultraegernummer,
            schulform = EXCLUDED.schulform,
            strasse = EXCLUDED.strasse,
            plz = EXCLUDED.plz,
            ort = EXCLUDED.ort,
            kreis = EXCLUDED.kreis,
            telefon = EXCLUDED.telefon,
            fax = EXCLUDED.fax,
            email = EXCLUDED.email,
            homepage = EXCLUDED.homepage,
            aufloesung = EXCLUDED.aufloesung,
            last_seen_at = now(),
            updated_at = now()
        """;

    private static final String INSERT_IMPORT_LOG = """
        INSERT INTO schuldatei_import
            (id, gestartet_am, beendet_am, erfolgreich, fehlermeldung, anzahl_schulen, anzahl_schultraeger,
             ausgeloest_von)
        VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?)
        RETURNING id, gestartet_am, beendet_am, erfolgreich, fehlermeldung, anzahl_schulen, anzahl_schultraeger,
                  ausgeloest_von
        """;

    private static final String SELECT_LETZTER_IMPORT = """
        SELECT id, gestartet_am, beendet_am, erfolgreich, fehlermeldung, anzahl_schulen, anzahl_schultraeger,
               ausgeloest_von
        FROM schuldatei_import
        ORDER BY gestartet_am DESC
        LIMIT 1
        """;

    private static final String SCHULEN_BASE = """
        SELECT sk.id, sk.bundeslandkennung, sk.schulnummer, sk.schulname, sk.schultraegernummer,
               tk.traegername AS schultraegername, sk.schulform, sk.strasse, sk.plz, sk.ort, sk.kreis,
               sk.telefon, sk.fax, sk.email, sk.homepage, sk.aufloesung,
               (sk.aufloesung IS NULL OR sk.aufloesung >= CURRENT_DATE) AS aktiv,
               sk.last_seen_at, sk.created_at, sk.updated_at
        FROM schule_katalog sk
        LEFT JOIN schultraeger_katalog tk
            ON tk.bundeslandkennung = sk.bundeslandkennung AND tk.traegernummer = sk.schultraegernummer
        WHERE (?::text IS NULL OR sk.schulname ILIKE '%' || ?::text || '%'
              OR sk.schulnummer ILIKE '%' || ?::text || '%' OR sk.ort ILIKE '%' || ?::text || '%')
          AND (?::text IS NULL OR sk.schultraegernummer = ?::text)
          AND (?::boolean IS NULL OR (sk.aufloesung IS NULL OR sk.aufloesung >= CURRENT_DATE) = ?::boolean)
        """;

    private static final String SCHULTRAEGER_BASE = """
        SELECT tk.id, tk.bundeslandkennung, tk.traegernummer, tk.traegername, tk.traegerschaftsart,
               tk.strasse, tk.plz, tk.ort, tk.aufloesung,
               (tk.aufloesung IS NULL OR tk.aufloesung >= CURRENT_DATE) AS aktiv,
               tk.last_seen_at, tk.created_at, tk.updated_at
        FROM schultraeger_katalog tk
        WHERE (?::text IS NULL OR tk.traegername ILIKE '%' || ?::text || '%'
              OR tk.traegernummer ILIKE '%' || ?::text || '%' OR tk.ort ILIKE '%' || ?::text || '%')
          AND (?::boolean IS NULL OR (tk.aufloesung IS NULL OR tk.aufloesung >= CURRENT_DATE) = ?::boolean)
        """;

    @Inject
    OperatorAccess operatorAccess;

    @Inject
    NrwSchuldateiClient nrwSchuldateiClient;

    /**
     * Ruft die NRW-Schuldatei ab (außerhalb der DB-Transaktion, da der Abruf mehrere Sekunden
     * dauern kann und dabei keine Datenbankverbindung blockieren soll) und aktualisiert
     * anschließend die Kataloge per Upsert. Ein Fehlschlag des Abrufs wirft keine Exception,
     * sondern wird als erfolgloser Import-Log-Eintrag festgehalten - der Refresh-Versuch selbst
     * gilt als durchgeführt und wird auditiert.
     */
    public SchuldateiImportStatusDto refresh(final String adminSubject) {
        final Instant gestartetAm = Instant.now();
        final NrwSchuldateiResult result = nrwSchuldateiClient.fetchSchuldatei();

        return operatorAccess.execute(adminSubject, AuditAction.SCHULDATEI_IMPORT, "schuldatei_import", connection -> {
            if (!result.success()) {
                final SchuldateiImportStatusDto dto = insertImportLog(connection, gestartetAm, false, result.message(),
                    null, null, adminSubject);
                return outcomeFor(dto, false);
            }

            for (final NrwSchultraegerEintrag traeger : result.schultraeger()) {
                upsertSchultraeger(connection, traeger);
            }
            for (final NrwSchuleEintrag schule : result.schulen()) {
                upsertSchule(connection, schule);
            }

            final SchuldateiImportStatusDto dto = insertImportLog(connection, gestartetAm, true, null,
                result.schulen().size(), result.schultraeger().size(), adminSubject);
            return outcomeFor(dto, true);
        });
    }

    /** Liefert den zuletzt protokollierten Importlauf, falls vorhanden (für die Schuldatei-Kachel). */
    public SchuldateiImportStatusDto letzterImport(final String adminSubject) {
        return operatorAccess.execute(adminSubject, AuditAction.SCHULDATEI_STATUS, "schuldatei_import", connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SELECT_LETZTER_IMPORT);
                 ResultSet resultSet = statement.executeQuery()) {
                final SchuldateiImportStatusDto dto = resultSet.next() ? toImportStatusDto(resultSet) : null;
                return OperatorOutcome.crossTenant(dto, null);
            }
        });
    }

    public SchuleKatalogPageDto listSchulen(final String adminSubject, final int page, final int size,
            final String query, final String schultraegernummer, final Boolean nurAktive) {
        return operatorAccess.execute(adminSubject, AuditAction.SCHULDATEI_SCHULEN_LIST, "schule_katalog", connection -> {
            final String q = normalisiert(query);
            final String traegernummerFilter = normalisiert(schultraegernummer);

            final List<SchuleKatalogDto> items = new ArrayList<>();
            final String selectSql = SCHULEN_BASE + "ORDER BY sk.schulnummer LIMIT ? OFFSET ?";
            try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
                bindSchulenFilter(statement, q, traegernummerFilter, nurAktive);
                statement.setInt(9, size);
                statement.setInt(10, page * size);
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        items.add(toSchuleDto(resultSet));
                    }
                }
            }

            final String countSql = "SELECT count(*) FROM (" + SCHULEN_BASE + ") AS zaehlung";
            long total;
            try (PreparedStatement statement = connection.prepareStatement(countSql)) {
                bindSchulenFilter(statement, q, traegernummerFilter, nurAktive);
                try (ResultSet resultSet = statement.executeQuery()) {
                    resultSet.next();
                    total = resultSet.getLong(1);
                }
            }

            return OperatorOutcome.crossTenant(new SchuleKatalogPageDto(items, page, size, total), null);
        });
    }

    public SchultraegerKatalogPageDto listSchultraeger(final String adminSubject, final int page, final int size,
            final String query, final Boolean nurAktive) {
        return operatorAccess.execute(adminSubject, AuditAction.SCHULDATEI_SCHULTRAEGER_LIST, "schultraeger_katalog",
            connection -> {
                final String q = normalisiert(query);

                final List<SchultraegerKatalogDto> items = new ArrayList<>();
                final String selectSql = SCHULTRAEGER_BASE + "ORDER BY tk.traegernummer LIMIT ? OFFSET ?";
                try (PreparedStatement statement = connection.prepareStatement(selectSql)) {
                    bindSchultraegerFilter(statement, q, nurAktive);
                    statement.setInt(7, size);
                    statement.setInt(8, page * size);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        while (resultSet.next()) {
                            items.add(toSchultraegerDto(resultSet));
                        }
                    }
                }

                final String countSql = "SELECT count(*) FROM (" + SCHULTRAEGER_BASE + ") AS zaehlung";
                long total;
                try (PreparedStatement statement = connection.prepareStatement(countSql)) {
                    bindSchultraegerFilter(statement, q, nurAktive);
                    try (ResultSet resultSet = statement.executeQuery()) {
                        resultSet.next();
                        total = resultSet.getLong(1);
                    }
                }

                return OperatorOutcome.crossTenant(new SchultraegerKatalogPageDto(items, page, size, total), null);
            });
    }

    private void bindSchulenFilter(final PreparedStatement statement, final String q, final String traegernummer,
            final Boolean nurAktive) throws SQLException {
        statement.setString(1, q);
        statement.setString(2, q);
        statement.setString(3, q);
        statement.setString(4, q);
        statement.setString(5, traegernummer);
        statement.setString(6, traegernummer);
        statement.setObject(7, nurAktive, Types.BOOLEAN);
        statement.setObject(8, nurAktive, Types.BOOLEAN);
    }

    private void bindSchultraegerFilter(final PreparedStatement statement, final String q, final Boolean nurAktive)
            throws SQLException {
        statement.setString(1, q);
        statement.setString(2, q);
        statement.setString(3, q);
        statement.setString(4, q);
        statement.setObject(5, nurAktive, Types.BOOLEAN);
        statement.setObject(6, nurAktive, Types.BOOLEAN);
    }

    private void upsertSchultraeger(final Connection connection, final NrwSchultraegerEintrag traeger) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_SCHULTRAEGER)) {
            statement.setString(1, BUNDESLANDKENNUNG);
            statement.setString(2, traeger.traegernummer());
            statement.setString(3, traeger.traegername());
            statement.setString(4, traeger.traegerschaftsart());
            statement.setString(5, traeger.strasse());
            statement.setString(6, traeger.plz());
            statement.setString(7, traeger.ort());
            statement.setDate(8, parseAufloesung(traeger.aufloesung()));
            statement.executeUpdate();
        }
    }

    private void upsertSchule(final Connection connection, final NrwSchuleEintrag schule) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_SCHULE)) {
            statement.setString(1, BUNDESLANDKENNUNG);
            statement.setString(2, schule.schulnummer());
            statement.setString(3, schule.schulname() == null ? schule.schulnummer() : schule.schulname());
            statement.setString(4, schule.schultraegernummer());
            statement.setString(5, schule.schulform());
            statement.setString(6, schule.strasse());
            statement.setString(7, schule.plz());
            statement.setString(8, schule.ort());
            statement.setString(9, schule.kreis());
            statement.setString(10, schule.telefon());
            statement.setString(11, schule.fax());
            statement.setString(12, schule.email());
            statement.setString(13, schule.homepage());
            statement.setDate(14, parseAufloesung(schule.aufloesung()));
            statement.executeUpdate();
        }
    }

    private SchuldateiImportStatusDto insertImportLog(final Connection connection, final Instant gestartetAm,
            final boolean erfolgreich, final String fehlermeldung, final Integer anzahlSchulen,
            final Integer anzahlSchultraeger, final String adminSubject) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_IMPORT_LOG)) {
            statement.setTimestamp(1, Timestamp.from(gestartetAm));
            statement.setTimestamp(2, Timestamp.from(Instant.now()));
            statement.setBoolean(3, erfolgreich);
            statement.setString(4, fehlermeldung);
            statement.setObject(5, anzahlSchulen);
            statement.setObject(6, anzahlSchultraeger);
            statement.setString(7, adminSubject);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return toImportStatusDto(resultSet);
            }
        }
    }

    private OperatorOutcome<SchuldateiImportStatusDto> outcomeFor(final SchuldateiImportStatusDto dto,
            final boolean erfolgreich) {
        final String detailsJson = "{\"erfolgreich\":" + erfolgreich
            + (dto.anzahlSchulen() == null ? "" : ",\"anzahlSchulen\":" + dto.anzahlSchulen())
            + (dto.anzahlSchultraeger() == null ? "" : ",\"anzahlSchultraeger\":" + dto.anzahlSchultraeger())
            + "}";
        return new OperatorOutcome<>(dto, dto.id(), null, detailsJson);
    }

    private Date parseAufloesung(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            final LocalDate parsed = LocalDate.parse(value.trim(), NRW_DATE);
            return Date.valueOf(parsed);
        } catch (final DateTimeParseException e) {
            return null;
        }
    }

    private String normalisiert(final String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private SchuleKatalogDto toSchuleDto(final ResultSet resultSet) throws SQLException {
        return new SchuleKatalogDto(
            (UUID) resultSet.getObject("id"),
            resultSet.getString("bundeslandkennung"),
            resultSet.getString("schulnummer"),
            resultSet.getString("schulname"),
            resultSet.getString("schultraegernummer"),
            resultSet.getString("schultraegername"),
            resultSet.getString("schulform"),
            resultSet.getString("strasse"),
            resultSet.getString("plz"),
            resultSet.getString("ort"),
            resultSet.getString("kreis"),
            resultSet.getString("telefon"),
            resultSet.getString("fax"),
            resultSet.getString("email"),
            resultSet.getString("homepage"),
            toLocalDate(resultSet.getDate("aufloesung")),
            resultSet.getBoolean("aktiv"),
            resultSet.getTimestamp("last_seen_at").toInstant(),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getTimestamp("updated_at").toInstant());
    }

    private SchultraegerKatalogDto toSchultraegerDto(final ResultSet resultSet) throws SQLException {
        return new SchultraegerKatalogDto(
            (UUID) resultSet.getObject("id"),
            resultSet.getString("bundeslandkennung"),
            resultSet.getString("traegernummer"),
            resultSet.getString("traegername"),
            resultSet.getString("traegerschaftsart"),
            resultSet.getString("strasse"),
            resultSet.getString("plz"),
            resultSet.getString("ort"),
            toLocalDate(resultSet.getDate("aufloesung")),
            resultSet.getBoolean("aktiv"),
            resultSet.getTimestamp("last_seen_at").toInstant(),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getTimestamp("updated_at").toInstant());
    }

    private SchuldateiImportStatusDto toImportStatusDto(final ResultSet resultSet) throws SQLException {
        return new SchuldateiImportStatusDto(
            (UUID) resultSet.getObject("id"),
            resultSet.getTimestamp("gestartet_am").toInstant(),
            toInstant(resultSet.getTimestamp("beendet_am")),
            resultSet.getBoolean("erfolgreich"),
            resultSet.getString("fehlermeldung"),
            (Integer) resultSet.getObject("anzahl_schulen"),
            (Integer) resultSet.getObject("anzahl_schultraeger"),
            resultSet.getString("ausgeloest_von"));
    }

    private LocalDate toLocalDate(final Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private Instant toInstant(final Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
