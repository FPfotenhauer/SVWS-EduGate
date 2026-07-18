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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Testet die reale HTTP-Implementierung des {@link SvwsConnectionTester}-Ports gegen einen
 * lokalen {@link HttpServer} (JDK-Bordmittel, keine zusätzliche Testabhängigkeit) - deckt den
 * Vertrag von {@code POST /api/schema/root/user/checkrootprivs} ab (200+true/false,
 * 401/403, sonstige Statuscodes), außerdem Verbindungsablehnung und Zeitüberschreitung.
 * Zentrale Regel: Kein Szenario darf eine Exception nach außen werfen oder rohe
 * Exception-/Response-Details in der Meldung offenlegen (ADR-006).
 */
class HttpSvwsConnectionTesterTest {

    private static final Duration SHORT_TIMEOUT = Duration.ofMillis(300);

    private final HttpSvwsConnectionTester tester = new HttpSvwsConnectionTester(SHORT_TIMEOUT, SHORT_TIMEOUT);

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void test_mit200UndTrue_liefertErfolg() throws IOException {
        server = startServer(exchange -> respond(exchange, 200, "true"));

        final SvwsConnectionTestResult result = tester.test(baseUrl(server), "user", "pass");

        assertThat(result.success()).isTrue();
    }

    @Test
    void test_mit200UndFalse_liefertFehlschlagOhneCredentialLeak() throws IOException {
        server = startServer(exchange -> respond(exchange, 200, "false"));

        final SvwsConnectionTestResult result = tester.test(baseUrl(server), "user", "geheimes-passwort");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).doesNotContain("geheimes-passwort");
    }

    @Test
    void test_mit401Status_liefertSicherenFehlschlag() throws IOException {
        server = startServer(exchange -> respond(exchange, 401, ""));

        final SvwsConnectionTestResult result = tester.test(baseUrl(server), "user", "pass");

        assertThat(result.success()).isFalse();
    }

    @Test
    void test_mit404Status_liefertFehlschlagMitStatuscode() throws IOException {
        server = startServer(exchange -> respond(exchange, 404, ""));

        final SvwsConnectionTestResult result = tester.test(baseUrl(server), "user", "pass");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("404");
    }

    @Test
    void test_mit500Status_liefertFehlschlag() throws IOException {
        server = startServer(exchange -> respond(exchange, 500, ""));

        final SvwsConnectionTestResult result = tester.test(baseUrl(server), "user", "pass");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("500");
    }

    @Test
    void test_sendetPfadUndCredentialsAlsBasicAuthUndJsonBody() throws IOException {
        final AtomicReference<String> gesehenerPfad = new AtomicReference<>();
        final AtomicReference<String> gesehenerBody = new AtomicReference<>();
        server = startServer(exchange -> {
            gesehenerPfad.set(exchange.getRequestURI().getPath());
            gesehenerBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "true");
        });

        tester.test(baseUrl(server), "svws-admin", "geheim\"quote");

        assertThat(gesehenerPfad.get()).isEqualTo("/api/schema/root/user/checkrootprivs");
        assertThat(gesehenerBody.get()).contains("\"user\":\"svws-admin\"").contains("\\\"quote");
    }

    @Test
    void test_mitVerweigerterVerbindung_liefertSicherenFehlschlagOhneException() throws IOException {
        final int closedPort = findClosedPort();

        final SvwsConnectionTestResult result = tester.test("http://127.0.0.1:" + closedPort, "user", "pass");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).doesNotContain("Exception").doesNotContain("java.net");
    }

    @Test
    void test_mitZeitueberschreitung_liefertSicherenFehlschlagOhneException() throws IOException {
        server = startServer(exchange -> {
            try {
                Thread.sleep(2000);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "true");
        });

        final SvwsConnectionTestResult result = tester.test(baseUrl(server), "user", "pass");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("Zeitüberschreitung");
    }

    @Test
    void test_mitTechnischUngueltigerBaseUrl_liefertSicherenFehlschlagOhneException() {
        final SvwsConnectionTestResult result = tester.test("keine-url", "user", "pass");

        assertThat(result.success()).isFalse();
    }

    @Test
    void testReachability_mit200_liefertErfolgOhneAuthHeader() throws IOException {
        final AtomicReference<String> gesehenerPfad = new AtomicReference<>();
        final AtomicReference<String> gesehenerAuthHeader = new AtomicReference<>();
        server = startServer(exchange -> {
            gesehenerPfad.set(exchange.getRequestURI().getPath());
            gesehenerAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "SVWS-Server erreichbar");
        });

        final SvwsConnectionTestResult result = tester.testReachability(baseUrl(server));

        assertThat(result.success()).isTrue();
        assertThat(gesehenerPfad.get()).isEqualTo("/status/alive");
        assertThat(gesehenerAuthHeader.get()).isNull();
    }

    @Test
    void testReachability_mit500_liefertFehlschlagMitStatuscode() throws IOException {
        server = startServer(exchange -> respond(exchange, 500, ""));

        final SvwsConnectionTestResult result = tester.testReachability(baseUrl(server));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("500");
    }

    @Test
    void testReachability_mitVerweigerterVerbindung_liefertSicherenFehlschlagOhneException() throws IOException {
        final int closedPort = findClosedPort();

        final SvwsConnectionTestResult result = tester.testReachability("http://127.0.0.1:" + closedPort);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).doesNotContain("Exception").doesNotContain("java.net");
    }

    @Test
    void testReachability_mitTechnischUngueltigerBaseUrl_liefertSicherenFehlschlagOhneException() {
        final SvwsConnectionTestResult result = tester.testReachability("keine-url");

        assertThat(result.success()).isFalse();
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
