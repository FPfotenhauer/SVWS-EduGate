package de.svws_nrw.edugate.core.svws;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Testet die reale HTTP-Implementierung des {@link SvwsConnectionTester}-Ports gegen einen
 * lokalen {@link HttpServer} (JDK-Bordmittel, keine zusätzliche Testabhängigkeit) - deckt
 * Erfolg, Fehlerstatuscodes, Verbindungsablehnung und Zeitüberschreitung ab. Zentrale Regel:
 * Kein Szenario darf eine Exception nach außen werfen oder rohe Exception-/Response-Details
 * in der Meldung offenlegen (ADR-006).
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
    void test_mit200Status_liefertErfolg() throws IOException {
        server = startServer(exchange -> respond(exchange, 200));

        final SvwsConnectionTestResult result = tester.test(baseUrl(server), "user", "pass");

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("200");
    }

    @Test
    void test_mit404Status_liefertFehlschlagMitStatuscode() throws IOException {
        server = startServer(exchange -> respond(exchange, 404));

        final SvwsConnectionTestResult result = tester.test(baseUrl(server), "user", "pass");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("404");
    }

    @Test
    void test_mit500Status_liefertFehlschlag() throws IOException {
        server = startServer(exchange -> respond(exchange, 500));

        final SvwsConnectionTestResult result = tester.test(baseUrl(server), "user", "pass");

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("500");
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
            respond(exchange, 200);
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

    private static HttpServer startServer(final HttpHandler handler) throws IOException {
        final HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/", handler);
        httpServer.start();
        return httpServer;
    }

    private static void respond(final HttpExchange exchange, final int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
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
