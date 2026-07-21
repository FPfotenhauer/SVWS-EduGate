package de.svws_nrw.edugate.core.schuldatei;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Testet die reale HTTP-Implementierung des {@link NrwSchuldateiClient}-Ports gegen einen lokalen
 * {@link HttpServer} mit synthetischen, an der echten NRW-Antwortstruktur orientierten Fixtures
 * (ADR-020-Nachtrag "Grundlagenrecherche vor Umsetzung" - Feldnamen und Verschachtelung wurden
 * gegen die realen Endpunkte verifiziert). Zentrale Regel: Kein Szenario darf eine Exception nach
 * außen werfen (ADR-006).
 */
class HttpNrwSchuldateiClientTest {

    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(500);

    private static final String KONTEN_MIT_SCHULE_UND_TRAEGER = """
        {
          "organisationseinheit": [
            {
              "schulnummer": "100000",
              "oeart": "1",
              "amtsbez1": "wird ignoriert",
              "aufloesung": "31.12.9999",
              "grunddaten": {
                "kurzbezeichnung": "Bochum, Testschule",
                "schultraegernummer": "10100",
                "schulform": [
                  {"schulformcode": "Schulform", "schulformwert": "00", "gueltigab": "01.01.1970", "gueltigbis": "31.12.1999"},
                  {"schulformcode": "Schulform", "schulformwert": "25", "gueltigab": "01.01.2000", "gueltigbis": "31.12.9999"},
                  {"schulformcode": "SchulformASD", "schulformwert": "XX", "gueltigab": "01.01.2000", "gueltigbis": "31.12.9999"}
                ]
              },
              "adressen": [
                {"strasse": "Girondelle 80", "postleitzahl": "44799", "ort": "Bochum",
                 "regionalschluessel": "05911000", "hauptstandortadresse": "1", "liegenschaft": "1",
                 "gueltigab": "01.01.1974", "gueltigbis": "31.12.9999"}
              ],
              "erreichbarkeiten": [
                {"codekey": "01", "codewert": "info@testschule.de", "kommgruppe": "1", "liegenschaft": "1",
                 "gueltigab": "01.01.1974", "gueltigbis": "31.12.9999"},
                {"codekey": "02", "codewert": "0234-123456", "kommgruppe": "1", "liegenschaft": "1",
                 "gueltigab": "01.01.1974", "gueltigbis": "31.12.9999"}
              ]
            },
            {
              "schulnummer": "10100",
              "oeart": "2",
              "amtsbez1": "Fallback-Name",
              "aufloesung": "31.12.9999",
              "grunddaten": {
                "kurzbezeichnung": "Bochum, Schulträger e.V.",
                "artdertraegerschaft": "35"
              },
              "adressen": [
                {"strasse": "Trägerstr. 1", "postleitzahl": "44777", "ort": "Bochum",
                 "hauptstandortadresse": "1", "gueltigab": "01.01.1970", "gueltigbis": "31.12.9999"}
              ]
            },
            {
              "schulnummer": "9999",
              "oeart": "3",
              "amtsbez1": "Schulaufsichtsbehörde - muss verworfen werden"
            }
          ]
        }
        """;

    private static final String KATALOG_STANDARD = """
        {
          "katalog": [
            {"katalog": "Traeger", "wert": "35",
             "bezeichnung": "Wirtschaftsunternehmen als Träger einer Betriebsberufsschule"},
            {"katalog": "Erreichbarkeit", "wert": "01", "bezeichnung": "E-Mail"},
            {"katalog": "Erreichbarkeit", "wert": "02", "bezeichnung": "Telefon"}
          ]
        }
        """;

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void fetchSchuldatei_liefertGefilterteUndAngereicherteEintraege() throws IOException {
        server = startServer(Map.of(
            "/konten", new Antwort(200, KONTEN_MIT_SCHULE_UND_TRAEGER),
            "/katalog", new Antwort(200, KATALOG_STANDARD)));
        final HttpNrwSchuldateiClient client = clientFor(server);

        final NrwSchuldateiResult result = client.fetchSchuldatei();

        assertThat(result.success()).isTrue();
        assertThat(result.schulen()).hasSize(1);
        assertThat(result.schultraeger()).hasSize(1);

        final NrwSchuleEintrag schule = result.schulen().get(0);
        assertThat(schule.schulnummer()).isEqualTo("100000");
        assertThat(schule.schulname()).isEqualTo("Bochum, Testschule");
        assertThat(schule.schultraegernummer()).isEqualTo("10100");
        assertThat(schule.schulform()).isEqualTo("25");
        assertThat(schule.strasse()).isEqualTo("Girondelle 80");
        assertThat(schule.plz()).isEqualTo("44799");
        assertThat(schule.ort()).isEqualTo("Bochum");
        assertThat(schule.kreis()).isEqualTo("05911");
        assertThat(schule.email()).isEqualTo("info@testschule.de");
        assertThat(schule.telefon()).isEqualTo("0234-123456");
        assertThat(schule.aufloesung()).isEqualTo("31.12.9999");

        final NrwSchultraegerEintrag traeger = result.schultraeger().get(0);
        assertThat(traeger.traegernummer()).isEqualTo("10100");
        assertThat(traeger.traegername()).isEqualTo("Bochum, Schulträger e.V.");
        assertThat(traeger.traegerschaftsart())
            .isEqualTo("Wirtschaftsunternehmen als Träger einer Betriebsberufsschule");
        assertThat(traeger.strasse()).isEqualTo("Trägerstr. 1");
    }

    @Test
    void fetchSchuldatei_verwirftNichtRelevanteOeartWerte() throws IOException {
        server = startServer(Map.of(
            "/konten", new Antwort(200, KONTEN_MIT_SCHULE_UND_TRAEGER),
            "/katalog", new Antwort(200, KATALOG_STANDARD)));
        final HttpNrwSchuldateiClient client = clientFor(server);

        final NrwSchuldateiResult result = client.fetchSchuldatei();

        final boolean enthaeltSchulaufsicht = result.schulen().stream().anyMatch(s -> "9999".equals(s.schulnummer()))
            || result.schultraeger().stream().anyMatch(t -> "9999".equals(t.traegernummer()));
        assertThat(enthaeltSchulaufsicht).isFalse();
    }

    @Test
    void fetchSchuldatei_mitKontenFehlschlag_liefertSicherenFehlschlagOhneException() throws IOException {
        server = startServer(Map.of(
            "/konten", new Antwort(500, ""),
            "/katalog", new Antwort(200, KATALOG_STANDARD)));
        final HttpNrwSchuldateiClient client = clientFor(server);

        final NrwSchuldateiResult result = client.fetchSchuldatei();

        assertThat(result.success()).isFalse();
        assertThat(result.schulen()).isEmpty();
        assertThat(result.schultraeger()).isEmpty();
        assertThat(result.message()).contains("500");
    }

    @Test
    void fetchSchuldatei_mitKatalogFehlschlag_liefertSicherenFehlschlag() throws IOException {
        server = startServer(Map.of(
            "/konten", new Antwort(200, KONTEN_MIT_SCHULE_UND_TRAEGER),
            "/katalog", new Antwort(404, "")));
        final HttpNrwSchuldateiClient client = clientFor(server);

        final NrwSchuldateiResult result = client.fetchSchuldatei();

        assertThat(result.success()).isFalse();
    }

    @Test
    void fetchSchuldatei_mitKaputtemJson_liefertSicherenFehlschlagOhneException() throws IOException {
        server = startServer(Map.of(
            "/konten", new Antwort(200, "{nicht valide"),
            "/katalog", new Antwort(200, KATALOG_STANDARD)));
        final HttpNrwSchuldateiClient client = clientFor(server);

        final NrwSchuldateiResult result = client.fetchSchuldatei();

        assertThat(result.success()).isFalse();
    }

    @Test
    void fetchSchuldatei_mitUnerwartetemFormat_liefertSicherenFehlschlag() throws IOException {
        server = startServer(Map.of(
            "/konten", new Antwort(200, "{\"nicht\":\"das erwartete Feld\"}"),
            "/katalog", new Antwort(200, KATALOG_STANDARD)));
        final HttpNrwSchuldateiClient client = clientFor(server);

        final NrwSchuldateiResult result = client.fetchSchuldatei();

        assertThat(result.success()).isFalse();
    }

    @Test
    void fetchSchuldatei_mitZeitueberschreitung_liefertSicherenFehlschlagOhneException() throws IOException {
        server = startServer(exchange -> {
            try {
                Thread.sleep(2000);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, KONTEN_MIT_SCHULE_UND_TRAEGER);
        });
        final HttpNrwSchuldateiClient client = clientFor(server);

        final NrwSchuldateiResult result = client.fetchSchuldatei();

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("Zeitüberschreitung");
    }

    @Test
    void fetchSchuldatei_schuleOhneSchultraegernummer_liefertNullStattException() throws IOException {
        final String konten = """
            {"organisationseinheit": [
              {"schulnummer": "1", "oeart": "1", "grunddaten": {"kurzbezeichnung": "Schule ohne Träger"}}
            ]}
            """;
        server = startServer(Map.of(
            "/konten", new Antwort(200, konten),
            "/katalog", new Antwort(200, "{\"katalog\":[]}")));
        final HttpNrwSchuldateiClient client = clientFor(server);

        final NrwSchuldateiResult result = client.fetchSchuldatei();

        assertThat(result.success()).isTrue();
        assertThat(result.schulen()).hasSize(1);
        assertThat(result.schulen().get(0).schultraegernummer()).isNull();
    }

    private HttpNrwSchuldateiClient clientFor(final HttpServer httpServer) {
        final String base = "http://127.0.0.1:" + httpServer.getAddress().getPort();
        return new HttpNrwSchuldateiClient(SHORT_TIMEOUT, SHORT_TIMEOUT, base + "/konten", base + "/katalog");
    }

    private record Antwort(int status, String body) {
    }

    private HttpServer startServer(final Map<String, Antwort> responses) throws IOException {
        final HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        for (final var entry : responses.entrySet()) {
            httpServer.createContext(entry.getKey(),
                exchange -> respond(exchange, entry.getValue().status(), entry.getValue().body()));
        }
        httpServer.start();
        return httpServer;
    }

    private static HttpServer startServer(final HttpHandler handler) throws IOException {
        final HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/", handler);
        httpServer.start();
        return httpServer;
    }

    private static void respond(final HttpExchange exchange, final int status, final String body) throws IOException {
        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }
}
