package de.svws_nrw.edugate.core.svws;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Testet die reale HTTP-Implementierung des {@link SvwsPrivilegedApiClient}-Ports gegen einen
 * lokalen {@link HttpServer} (JDK-Bordmittel, keine zusätzliche Testabhängigkeit) - deckt den
 * Vertrag von {@code GET /api/schema/liste/svws} ab (200+Array, 401/403, sonstige Statuscodes,
 * kaputtes JSON), außerdem Verbindungsablehnung und Zeitüberschreitung. Zentrale Regel: Kein
 * Szenario darf eine Exception nach außen werfen oder rohe Exception-/Response-Details bzw.
 * Zugangsdaten in der Meldung offenlegen (ADR-006).
 */
class HttpSvwsPrivilegedApiClientTest {

    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(300);

    private final HttpSvwsPrivilegedApiClient client = new HttpSvwsPrivilegedApiClient(SHORT_TIMEOUT, SHORT_TIMEOUT);

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void listSchemas_mit200UndArray_liefertGeparsteEintraege() throws IOException {
        final String body = """
            [{"name":"123456","username":"123456","isSVWS":true,"revision":3,"isTainted":false,
              "isInConfig":true,"isDeactivated":false}]
            """;
        server = startServer(exchange -> respond(exchange, 200, body));

        final SvwsSchemaListResult result = client.listSchemas(baseUrl(server), "user", "pass");

        assertThat(result.success()).isTrue();
        assertThat(result.entries()).hasSize(1);
        final SvwsSchemaListeEintrag eintrag = result.entries().get(0);
        assertThat(eintrag.name()).isEqualTo("123456");
        assertThat(eintrag.username()).isEqualTo("123456");
        assertThat(eintrag.isSvws()).isTrue();
        assertThat(eintrag.revision()).isEqualTo(3L);
        assertThat(eintrag.isTainted()).isFalse();
        assertThat(eintrag.isInConfig()).isTrue();
        assertThat(eintrag.isDeactivated()).isFalse();
    }

    @Test
    void listSchemas_mit200UndLeeremArray_liefertLeereListe() throws IOException {
        server = startServer(exchange -> respond(exchange, 200, "[]"));

        final SvwsSchemaListResult result = client.listSchemas(baseUrl(server), "user", "pass");

        assertThat(result.success()).isTrue();
        assertThat(result.entries()).isEmpty();
    }

    @Test
    void listSchemas_mit403_liefertSicherenFehlschlagOhneCredentialLeak() throws IOException {
        server = startServer(exchange -> respond(exchange, 403, ""));

        final SvwsSchemaListResult result = client.listSchemas(baseUrl(server), "user", "geheimes-passwort");

        assertThat(result.success()).isFalse();
        assertThat(result.entries()).isEmpty();
        assertThat(result.message()).doesNotContain("geheimes-passwort");
    }

    @Test
    void listSchemas_mit401_liefertSicherenFehlschlag() throws IOException {
        server = startServer(exchange -> respond(exchange, 401, ""));

        final SvwsSchemaListResult result = client.listSchemas(baseUrl(server), "user", "pass");

        assertThat(result.success()).isFalse();
    }

    @Test
    void listSchemas_mit500_liefertFehlschlagMitStatuscode() throws IOException {
        server = startServer(exchange -> respond(exchange, 500, ""));

        final SvwsSchemaListResult result = client.listSchemas(baseUrl(server), "user", "pass");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("500");
    }

    @Test
    void listSchemas_mitKaputtemJson_liefertSicherenFehlschlagOhneException() throws IOException {
        server = startServer(exchange -> respond(exchange, 200, "{nicht valide"));

        final SvwsSchemaListResult result = client.listSchemas(baseUrl(server), "user", "pass");

        assertThat(result.success()).isFalse();
        assertThat(result.entries()).isEmpty();
    }

    @Test
    void listSchemas_mitUnerwartetemJsonTyp_liefertSicherenFehlschlag() throws IOException {
        server = startServer(exchange -> respond(exchange, 200, "{\"nicht\":\"ein-array\"}"));

        final SvwsSchemaListResult result = client.listSchemas(baseUrl(server), "user", "pass");

        assertThat(result.success()).isFalse();
    }

    @Test
    void listSchemas_sendetPfadUndCredentialsAlsBasicAuth() throws IOException {
        final AtomicReference<String> gesehenerPfad = new AtomicReference<>();
        final AtomicReference<String> gesehenerAuthHeader = new AtomicReference<>();
        server = startServer(exchange -> {
            gesehenerPfad.set(exchange.getRequestURI().getPath());
            gesehenerAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "[]");
        });

        client.listSchemas(baseUrl(server), "svws-admin", "geheim");

        assertThat(gesehenerPfad.get()).isEqualTo("/api/schema/liste/svws");
        assertThat(gesehenerAuthHeader.get()).startsWith("Basic ");
    }

    @Test
    void listSchemas_mitVerweigerterVerbindung_liefertSicherenFehlschlagOhneException() throws IOException {
        final int closedPort = findClosedPort();

        final SvwsSchemaListResult result = client.listSchemas("http://127.0.0.1:" + closedPort, "user", "pass");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).doesNotContain("Exception").doesNotContain("java.net");
    }

    @Test
    void listSchemas_mitZeitueberschreitung_liefertSicherenFehlschlagOhneException() throws IOException {
        server = startServer(exchange -> {
            try {
                Thread.sleep(2000);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "[]");
        });

        final SvwsSchemaListResult result = client.listSchemas(baseUrl(server), "user", "pass");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("Zeitüberschreitung");
    }

    @Test
    void listSchemas_mitTechnischUngueltigerBaseUrl_liefertSicherenFehlschlagOhneException() {
        final SvwsSchemaListResult result = client.listSchemas("keine-url", "user", "pass");

        assertThat(result.success()).isFalse();
    }

    @Test
    void listSchemas_mitBlockiertemZielnetz_liefertSicherenFehlschlagOhneVerbindungsversuch() throws IOException {
        server = startServer(exchange -> respond(exchange, 200, "[]"));
        final HttpSvwsPrivilegedApiClient guardedClient =
            new HttpSvwsPrivilegedApiClient(SHORT_TIMEOUT, SHORT_TIMEOUT, null, SvwsTargetGuard.defaultDeny());

        final SvwsSchemaListResult result = guardedClient.listSchemas(baseUrl(server), "user", "pass");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).doesNotContain("127.0.0.1");
    }

    @Test
    void listSchemas_mitFreigegebenemZielnetz_ruftServerTrotzdemAuf() throws IOException {
        server = startServer(exchange -> respond(exchange, 200, "[]"));
        final HttpSvwsPrivilegedApiClient guardedClient = new HttpSvwsPrivilegedApiClient(
            SHORT_TIMEOUT, SHORT_TIMEOUT, null, SvwsTargetGuard.withAllowedNetworks(List.of("127.0.0.1/32")));

        final SvwsSchemaListResult result = guardedClient.listSchemas(baseUrl(server), "user", "pass");

        assertThat(result.success()).isTrue();
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

    private static String baseUrl(final HttpServer httpServer) {
        return "http://127.0.0.1:" + httpServer.getAddress().getPort();
    }

    private static int findClosedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
