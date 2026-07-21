package de.svws_nrw.edugate.core.schuldatei;

import de.svws_nrw.edugate.core.svws.JsonParseException;
import de.svws_nrw.edugate.core.svws.MinimalJsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.net.ssl.SSLException;

/**
 * HTTP-Implementierung des {@link NrwSchuldateiClient}-Ports (ADR-020-Nachtrag "Grundlagenrecherche
 * vor Umsetzung"). Ruft die zwei öffentlichen Export-Endpunkte des Schulministeriums NRW ab
 * ({@code .../export/json/konten}, {@code .../export/json/katalog}), filtert die darin enthaltenen
 * Organisationseinheiten auf {@code oeart=1} (Schule) und {@code oeart=2} (Schulträger) und
 * verwirft alle anderen Arten (Schulaufsicht, Studienseminare usw.).
 *
 * <p>Die Extraktionslogik (Adress-/Schulform-/Kontaktauswahl je nach Gültigkeitszeitraum) orientiert
 * sich an der bereits im Projekt {@code SVWS-Main-Server} (Paket {@code de.schultraeger}) bewährten
 * Auswertung, filtert aber - anders als dort - bewusst nach {@code oeart}, statt jede
 * Organisationseinheit mit gesetzter Nummer undifferenziert zu übernehmen.
 *
 * <p>Netzwerk-, Protokoll- und Parsingfehler werden nie als Exception nach außen gereicht, sondern
 * immer in ein sicheres {@link NrwSchuldateiResult#failure(String)} übersetzt (ADR-006).
 */
public final class HttpNrwSchuldateiClient implements NrwSchuldateiClient {

    private static final String DEFAULT_KONTEN_URL =
        "https://www.schulministerium.nrw.de/BiPo/Schuldatei/SchuldateiDatenService/export/json/konten";
    private static final String DEFAULT_KATALOG_URL =
        "https://www.schulministerium.nrw.de/BiPo/Schuldatei/SchuldateiDatenService/export/json/katalog";
    private static final String OEART_SCHULE = "1";
    private static final String OEART_SCHULTRAEGER = "2";
    private static final DateTimeFormatter NRW_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY);

    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final String kontenUrl;
    private final String katalogUrl;

    public HttpNrwSchuldateiClient() {
        this(Duration.ofSeconds(10), Duration.ofMinutes(2), DEFAULT_KONTEN_URL, DEFAULT_KATALOG_URL);
    }

    /** Für Tests: erlaubt kurze Timeouts und das Umleiten der beiden Export-URLs auf einen lokalen Testserver. */
    HttpNrwSchuldateiClient(final Duration connectTimeout, final Duration requestTimeout, final String kontenUrl,
            final String katalogUrl) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        this.requestTimeout = requestTimeout;
        this.kontenUrl = kontenUrl;
        this.katalogUrl = katalogUrl;
    }

    @Override
    public NrwSchuldateiResult fetchSchuldatei() {
        final List<Map<String, Object>> organisationseinheiten;
        try {
            organisationseinheiten = fetchOrganisationseinheiten(kontenUrl);
        } catch (final NrwFetchException e) {
            return NrwSchuldateiResult.failure(e.getMessage());
        }

        final List<Map<String, Object>> katalogEintraege;
        try {
            katalogEintraege = fetchKatalogListe(katalogUrl);
        } catch (final NrwFetchException e) {
            return NrwSchuldateiResult.failure(e.getMessage());
        }

        final Map<String, String> traegerschaftsartByCode = buildCodeMap(katalogEintraege, "Traeger");
        final Map<String, String> erreichbarkeitTypeByCode = buildErreichbarkeitTypeMap(katalogEintraege);
        final LocalDate today = LocalDate.now();

        final List<NrwSchuleEintrag> schulen = new ArrayList<>();
        final List<NrwSchultraegerEintrag> schultraeger = new ArrayList<>();
        for (final Map<String, Object> org : organisationseinheiten) {
            final String schulnummer = stringField(org, "schulnummer");
            final String oeart = stringField(org, "oeart");
            if (schulnummer == null || schulnummer.isBlank() || oeart == null) {
                continue;
            }
            if (OEART_SCHULE.equals(oeart)) {
                schulen.add(extractSchule(org, schulnummer, erreichbarkeitTypeByCode, today));
            } else if (OEART_SCHULTRAEGER.equals(oeart)) {
                schultraeger.add(extractSchultraeger(org, schulnummer, traegerschaftsartByCode));
            }
        }

        return NrwSchuldateiResult.success(schulen, schultraeger);
    }

    private NrwSchuleEintrag extractSchule(final Map<String, Object> org, final String schulnummer,
            final Map<String, String> erreichbarkeitTypeByCode, final LocalDate today) {
        final Map<?, ?> grunddaten = mapField(org, "grunddaten");
        final String schulname = grunddaten == null ? null : stringField(grunddaten, "kurzbezeichnung");
        final String schultraegernummer = grunddaten == null ? null : stringField(grunddaten, "schultraegernummer");
        final String schulform = grunddaten == null ? null : selectSchulform(listField(grunddaten, "schulform"), today);

        final Map<?, ?> adresse = selectValidAdresse(listField(org, "adressen"), today);
        final String strasse = adresse == null ? null : stringField(adresse, "strasse");
        final String plz = adresse == null ? null : stringField(adresse, "postleitzahl");
        final String ort = adresse == null ? null : stringField(adresse, "ort");
        final String kreis = extractKreis(adresse);
        final String preferredLiegenschaft = adresse == null ? null : stringField(adresse, "liegenschaft");

        final Kontakt kontakt = extractKontakt(listField(org, "erreichbarkeiten"), erreichbarkeitTypeByCode,
            preferredLiegenschaft, today);

        return new NrwSchuleEintrag(schulnummer, blankToNull(schulname), blankToNull(schultraegernummer), schulform,
            strasse, plz, ort, kreis, kontakt.telefon(), kontakt.fax(), kontakt.email(), kontakt.homepage(),
            stringField(org, "aufloesung"));
    }

    private NrwSchultraegerEintrag extractSchultraeger(final Map<String, Object> org, final String traegernummer,
            final Map<String, String> traegerschaftsartByCode) {
        final Map<?, ?> grunddaten = mapField(org, "grunddaten");
        String traegername = grunddaten == null ? null : blankToNull(stringField(grunddaten, "kurzbezeichnung"));
        if (traegername == null) {
            traegername = blankToNull(stringField(org, "amtsbez1"));
        }
        final String traegerschaftscode = grunddaten == null ? null : stringField(grunddaten, "artdertraegerschaft");
        final String traegerschaftsart = traegerschaftscode == null ? null : traegerschaftsartByCode.get(traegerschaftscode);

        final Map<?, ?> adresse = selectValidAdresse(listField(org, "adressen"), LocalDate.now());
        final String strasse = adresse == null ? null : stringField(adresse, "strasse");
        final String plz = adresse == null ? null : stringField(adresse, "postleitzahl");
        final String ort = adresse == null ? null : stringField(adresse, "ort");

        return new NrwSchultraegerEintrag(traegernummer, traegername == null ? traegernummer : traegername,
            traegerschaftsart, strasse, plz, ort, stringField(org, "aufloesung"));
    }

    private String selectSchulform(final List<?> schulformen, final LocalDate today) {
        if (schulformen == null) {
            return null;
        }
        Map<?, ?> fallback = null;
        for (final Object rawEntry : schulformen) {
            if (!(rawEntry instanceof Map<?, ?> entry)) {
                continue;
            }
            if (!"Schulform".equals(stringField(entry, "schulformcode"))) {
                continue;
            }
            if (fallback == null) {
                fallback = entry;
            }
            if (isValidForDate(entry, today)) {
                return stringField(entry, "schulformwert");
            }
        }
        return fallback == null ? null : stringField(fallback, "schulformwert");
    }

    private Map<?, ?> selectValidAdresse(final List<?> adressen, final LocalDate today) {
        if (adressen == null || adressen.isEmpty()) {
            return null;
        }
        final List<Map<?, ?>> valid = new ArrayList<>();
        for (final Object rawEntry : adressen) {
            if (rawEntry instanceof Map<?, ?> entry && isValidForDate(entry, today)) {
                valid.add(entry);
            }
        }
        if (valid.isEmpty()) {
            return adressen.get(0) instanceof Map<?, ?> first ? first : null;
        }
        for (final Map<?, ?> entry : valid) {
            if ("1".equals(stringField(entry, "hauptstandortadresse"))) {
                return entry;
            }
        }
        return valid.get(0);
    }

    private String extractKreis(final Map<?, ?> adresse) {
        if (adresse == null) {
            return null;
        }
        final String regionalschluessel = stringField(adresse, "regionalschluessel");
        if (regionalschluessel != null && regionalschluessel.length() >= 5) {
            return regionalschluessel.substring(0, 5);
        }
        return null;
    }

    private record Kontakt(String telefon, String fax, String email, String homepage) {
        private static Kontakt empty() {
            return new Kontakt(null, null, null, null);
        }
    }

    private Kontakt extractKontakt(final List<?> erreichbarkeiten, final Map<String, String> typeByCode,
            final String preferredLiegenschaft, final LocalDate today) {
        if (erreichbarkeiten == null) {
            return Kontakt.empty();
        }
        final List<Map<?, ?>> valid = new ArrayList<>();
        for (final Object rawEntry : erreichbarkeiten) {
            if (rawEntry instanceof Map<?, ?> entry && isValidForDate(entry, today)) {
                valid.add(entry);
            }
        }
        valid.sort((a, b) -> Integer.compare(erreichbarkeitScore(b, preferredLiegenschaft),
            erreichbarkeitScore(a, preferredLiegenschaft)));

        String telefon = null;
        String fax = null;
        String email = null;
        String homepage = null;
        for (final Map<?, ?> entry : valid) {
            final String codeKey = stringField(entry, "codekey");
            final String codeWert = blankToNull(stringField(entry, "codewert"));
            if (codeKey == null || codeWert == null) {
                continue;
            }
            final String type = typeByCode.getOrDefault(codeKey, fallbackErreichbarkeitType(codeKey));
            if (type == null) {
                continue;
            }
            switch (type) {
                case "telefon" -> telefon = telefon == null ? codeWert : telefon;
                case "fax" -> fax = fax == null ? codeWert : fax;
                case "email" -> email = email == null ? codeWert : email;
                case "homepage" -> homepage = homepage == null ? codeWert : homepage;
                default -> { /* unbekannter Typ - ignorieren */ }
            }
        }
        return new Kontakt(telefon, fax, email, homepage);
    }

    private int erreichbarkeitScore(final Map<?, ?> erreichbarkeit, final String preferredLiegenschaft) {
        int score = 0;
        if ("1".equals(stringField(erreichbarkeit, "kommgruppe"))) {
            score += 2;
        }
        final String liegenschaft = stringField(erreichbarkeit, "liegenschaft");
        if (preferredLiegenschaft != null && preferredLiegenschaft.equals(liegenschaft)) {
            score += 1;
        }
        return score;
    }

    private String fallbackErreichbarkeitType(final String codeKey) {
        return switch (codeKey) {
            case "01", "0" -> "email";
            case "02", "03" -> "telefon";
            case "04" -> "fax";
            case "09" -> "homepage";
            default -> null;
        };
    }

    private Map<String, String> buildCodeMap(final List<Map<String, Object>> katalogEintraege, final String katalogName) {
        final Map<String, String> map = new HashMap<>();
        for (final Map<String, Object> entry : katalogEintraege) {
            if (!katalogName.equalsIgnoreCase(stringField(entry, "katalog"))) {
                continue;
            }
            final String wert = stringField(entry, "wert");
            final String bezeichnung = stringField(entry, "bezeichnung");
            if (wert != null && bezeichnung != null) {
                map.put(wert, bezeichnung);
            }
        }
        return map;
    }

    private Map<String, String> buildErreichbarkeitTypeMap(final List<Map<String, Object>> katalogEintraege) {
        final Map<String, String> map = new HashMap<>();
        for (final Map.Entry<String, String> entry : buildCodeMap(katalogEintraege, "Erreichbarkeit").entrySet()) {
            final String type = classifyErreichbarkeit(entry.getValue());
            if (type != null) {
                map.put(entry.getKey(), type);
            }
        }
        return map;
    }

    private String classifyErreichbarkeit(final String bezeichnung) {
        final String normalized = bezeichnung.toLowerCase(Locale.GERMANY);
        if (normalized.contains("e-mail") || normalized.contains("email") || normalized.contains("de-mail")) {
            return "email";
        }
        if (normalized.contains("telefon")) {
            return "telefon";
        }
        if (normalized.contains("fax")) {
            return "fax";
        }
        if (normalized.contains("web")) {
            return "homepage";
        }
        return null;
    }

    private boolean isValidForDate(final Map<?, ?> entry, final LocalDate today) {
        final LocalDate validFrom = parseNrwDate(stringField(entry, "gueltigab"));
        final LocalDate validUntil = parseNrwDate(stringField(entry, "gueltigbis"));
        if (validFrom != null && today.isBefore(validFrom)) {
            return false;
        }
        return validUntil == null || !today.isAfter(validUntil);
    }

    private LocalDate parseNrwDate(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String datePart = value.trim();
        final int spaceIndex = datePart.indexOf(' ');
        if (spaceIndex > 0) {
            datePart = datePart.substring(0, spaceIndex);
        }
        try {
            return LocalDate.parse(datePart, NRW_DATE);
        } catch (final DateTimeParseException e) {
            return null;
        }
    }

    private List<Map<String, Object>> fetchOrganisationseinheiten(final String url) {
        final Object parsed = fetchAndParse(url);
        if (!(parsed instanceof Map<?, ?> map) || !(map.get("organisationseinheit") instanceof List<?> list)) {
            throw new NrwFetchException("Antwort der NRW-Schuldatei hatte ein unerwartetes Format.");
        }
        return castEntries(list);
    }

    private List<Map<String, Object>> fetchKatalogListe(final String url) {
        final Object parsed = fetchAndParse(url);
        final List<?> list;
        if (parsed instanceof Map<?, ?> map && map.get("katalog") instanceof List<?> katalogList) {
            list = katalogList;
        } else if (parsed instanceof List<?> rawList) {
            list = rawList;
        } else {
            throw new NrwFetchException("Antwort des NRW-Schuldatei-Katalogs hatte ein unerwartetes Format.");
        }
        return castEntries(list);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castEntries(final List<?> list) {
        final List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (final Object entry : list) {
            if (entry instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }

    private Object fetchAndParse(final String url) {
        final HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                .header("Accept", "application/json")
                .header("User-Agent", "SVWS-EduGate/1.0")
                .timeout(requestTimeout)
                .GET()
                .build();
        } catch (final IllegalArgumentException e) {
            throw new NrwFetchException("Die NRW-Schuldatei-URL ist technisch ungültig.");
        }

        final HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (final HttpTimeoutException e) {
            throw new NrwFetchException("Zeitüberschreitung beim Abruf der NRW-Schuldatei.");
        } catch (final SSLException e) {
            throw new NrwFetchException("TLS-/Zertifikatsfehler beim Abruf der NRW-Schuldatei.");
        } catch (final IOException e) {
            throw new NrwFetchException("Verbindung zur NRW-Schuldatei war nicht möglich (Netzwerkfehler).");
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NrwFetchException("Abruf der NRW-Schuldatei wurde unterbrochen.");
        }

        if (response.statusCode() != 200) {
            throw new NrwFetchException("NRW-Schuldatei antwortete mit Status " + response.statusCode() + ".");
        }
        try {
            return MinimalJsonParser.parse(response.body());
        } catch (final JsonParseException e) {
            throw new NrwFetchException("Antwort der NRW-Schuldatei konnte nicht gelesen werden.");
        }
    }

    private String stringField(final Map<?, ?> map, final String key) {
        final Object value = map.get(key);
        return value instanceof String s ? s : null;
    }

    private Map<?, ?> mapField(final Map<?, ?> map, final String key) {
        final Object value = map.get(key);
        return value instanceof Map<?, ?> m ? m : null;
    }

    private List<?> listField(final Map<?, ?> map, final String key) {
        final Object value = map.get(key);
        return value instanceof List<?> l ? l : null;
    }

    private String blankToNull(final String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** Interne, unchecked Ausnahme - wird ausschließlich innerhalb dieser Klasse gefangen und in {@link NrwSchuldateiResult#failure(String)} übersetzt. */
    private static final class NrwFetchException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private NrwFetchException(final String message) {
            super(message);
        }
    }
}
