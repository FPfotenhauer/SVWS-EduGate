package de.svws_nrw.edugate.core.svws;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;

/**
 * HTTP-Implementierung des {@link SvwsConnectionTester}-Ports (ADR-006, Phase 1).
 *
 * <p>Ruft die Base-URL der SVWS-Instanz per HTTP-GET mit Basic-Auth auf. Die SVWS-API
 * definiert (Stand dieses Auftrags) noch keinen dedizierten Status-/Health-Endpunkt in
 * diesem Repository – der Aufruf der Base-URL selbst ist ein pragmatischer
 * Phase-1-Platzhalter und kann später ohne Änderung des {@link SvwsConnectionTester}-Ports
 * oder seiner Aufrufer auf einen spezifischeren Pfad umgestellt werden (siehe
 * `docs/entwicklung/svws-server-api.md`: `/status/alive` bzw. die Privileged-API-Endpunkte
 * `checkrootprivs`/`checkpwd` sind voraussichtlich die fachlich passenderen Ziele).
 *
 * <p>Netzwerk- und Protokollfehler werden nie als Exception nach außen gereicht, sondern
 * immer in ein sicheres {@link SvwsConnectionTestResult#failure(String)} übersetzt – die
 * Meldung enthält nie {@code Exception#getMessage()} (kann interne Netzwerkdetails jenseits
 * der ohnehin bekannten Base-URL enthalten), sondern eine feste, kurze Kategorie.
 */
public final class HttpSvwsConnectionTester implements SvwsConnectionTester {

    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public HttpSvwsConnectionTester() {
        this(Duration.ofSeconds(5), Duration.ofSeconds(8), null);
    }

    /**
     * @param sslContext optional - nur für lokale Entwicklung mit einem
     *      {@link DevTrustStoreSslContext}, um selbstsignierte Test-SVWS-Instanzen additiv zu
     *      vertrauen. {@code null} verwendet die Standard-JVM-Vertrauenskette (Produktionsfall).
     */
    public HttpSvwsConnectionTester(final SSLContext sslContext) {
        this(Duration.ofSeconds(5), Duration.ofSeconds(8), sslContext);
    }

    HttpSvwsConnectionTester(final Duration connectTimeout, final Duration requestTimeout) {
        this(connectTimeout, requestTimeout, null);
    }

    HttpSvwsConnectionTester(final Duration connectTimeout, final Duration requestTimeout, final SSLContext sslContext) {
        final HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(connectTimeout);
        if (sslContext != null) {
            builder.sslContext(sslContext);
        }
        this.httpClient = builder.build();
        this.requestTimeout = requestTimeout;
    }

    @Override
    public SvwsConnectionTestResult test(final String baseUrl, final String username, final String password) {
        final HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(baseUrl))
                .header("Authorization", basicAuthHeader(username, password))
                .timeout(requestTimeout)
                .GET()
                .build();
        } catch (final IllegalArgumentException e) {
            return SvwsConnectionTestResult.failure("Die Base-URL ist technisch ungültig.");
        }

        try {
            final HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            final int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return SvwsConnectionTestResult.success("Verbindung erfolgreich (Status " + status + ").");
            }
            return SvwsConnectionTestResult.failure("SVWS-Instanz antwortete mit Status " + status + ".");
        } catch (final HttpTimeoutException e) {
            return SvwsConnectionTestResult.failure("Zeitüberschreitung beim Verbindungsaufbau.");
        } catch (final SSLException e) {
            return SvwsConnectionTestResult.failure("TLS-/Zertifikatsfehler bei der Verbindung.");
        } catch (final IOException e) {
            return SvwsConnectionTestResult.failure("Verbindung zur SVWS-Instanz war nicht möglich (Netzwerkfehler).");
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return SvwsConnectionTestResult.failure("Verbindungstest wurde unterbrochen.");
        }
    }

    private static String basicAuthHeader(final String username, final String password) {
        final String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
