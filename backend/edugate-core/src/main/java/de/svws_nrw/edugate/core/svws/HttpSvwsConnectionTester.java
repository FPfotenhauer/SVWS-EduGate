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
import java.util.function.Function;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;

/**
 * HTTP-Implementierung des {@link SvwsConnectionTester}-Ports (ADR-006).
 *
 * <p>{@link #test(String, String, String)} ruft {@code POST /api/schema/root/user/checkrootprivs}
 * der SVWS-Privileged-API auf (siehe {@code docs/entwicklung/svws-server-api.md}). Dieser
 * Endpunkt prüft in einem Aufruf sowohl Erreichbarkeit als auch Gültigkeit der übergebenen
 * Zugangsdaten <b>und</b>, ob der Benutzer privilegierte (root-)Rechte auf der Datenbank hat -
 * fachlich genau das, was für {@code svws_instanz}-Credentials benötigt wird. Die Zugangsdaten
 * werden sowohl als HTTP-Basic-Auth (vom SVWS-Server global vor der eigentlichen Methode
 * durchgesetzt) als auch im JSON-Body (vom Endpunkt selbst geprüft, Feld für Feld gegen die
 * Datenbank) übertragen.
 *
 * <p>{@link #testReachability(String)} ruft stattdessen das unauthentifizierte
 * {@code GET /status/alive} auf - für den Fall, dass (noch) keine Zugangsdaten hinterlegt sind.
 *
 * <p>Netzwerk- und Protokollfehler werden nie als Exception nach außen gereicht, sondern
 * immer in ein sicheres {@link SvwsConnectionTestResult#failure(String)} übersetzt – die
 * Meldung enthält nie {@code Exception#getMessage()} (kann interne Netzwerkdetails jenseits
 * der ohnehin bekannten Base-URL enthalten), sondern eine feste, kurze Kategorie. Die
 * Zugangsdaten selbst erscheinen niemals in einer Ergebnis-Meldung.
 */
public final class HttpSvwsConnectionTester implements SvwsConnectionTester {

    private static final String CHECK_ROOT_PRIVS_PATH = "/api/schema/root/user/checkrootprivs";
    private static final String ALIVE_PATH = "/status/alive";

    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final SvwsTargetGuard targetGuard;

    /** Verwendet {@link SvwsTargetGuard#defaultDeny()} - sicherster Default ohne weitere Konfiguration. */
    public HttpSvwsConnectionTester() {
        this(Duration.ofSeconds(5), Duration.ofSeconds(8), null, SvwsTargetGuard.defaultDeny());
    }

    /**
     * @param sslContext optional - nur für lokale Entwicklung mit einem
     *      {@link DevTrustStoreSslContext}, um selbstsignierte Test-SVWS-Instanzen additiv zu
     *      vertrauen. {@code null} verwendet die Standard-JVM-Vertrauenskette (Produktionsfall).
     */
    public HttpSvwsConnectionTester(final SSLContext sslContext) {
        this(Duration.ofSeconds(5), Duration.ofSeconds(8), sslContext, SvwsTargetGuard.defaultDeny());
    }

    /**
     * @param sslContext optional, siehe {@link #HttpSvwsConnectionTester(SSLContext)}.
     * @param targetGuard SSRF-Schutz (Issue #17) - entscheidet, gegen welche aufgelösten
     *      Zieladressen Verbindungstests überhaupt zugelassen werden.
     */
    public HttpSvwsConnectionTester(final SSLContext sslContext, final SvwsTargetGuard targetGuard) {
        this(Duration.ofSeconds(5), Duration.ofSeconds(8), sslContext, targetGuard);
    }

    HttpSvwsConnectionTester(final Duration connectTimeout, final Duration requestTimeout) {
        this(connectTimeout, requestTimeout, null, SvwsTargetGuard.allowAll());
    }

    HttpSvwsConnectionTester(final Duration connectTimeout, final Duration requestTimeout, final SSLContext sslContext) {
        this(connectTimeout, requestTimeout, sslContext, SvwsTargetGuard.allowAll());
    }

    HttpSvwsConnectionTester(
            final Duration connectTimeout,
            final Duration requestTimeout,
            final SSLContext sslContext,
            final SvwsTargetGuard targetGuard) {
        final HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(connectTimeout);
        if (sslContext != null) {
            builder.sslContext(sslContext);
        }
        this.httpClient = builder.build();
        this.requestTimeout = requestTimeout;
        this.targetGuard = targetGuard;
    }

    @Override
    public SvwsConnectionTestResult test(final String baseUrl, final String username, final String password) {
        final URI uri;
        final HttpRequest.Builder requestBuilder;
        try {
            uri = URI.create(stripTrailingSlash(baseUrl) + CHECK_ROOT_PRIVS_PATH);
            final String body = "{\"user\":" + jsonString(username) + ",\"password\":" + jsonString(password) + "}";
            requestBuilder = HttpRequest.newBuilder(uri)
                .header("Authorization", basicAuthHeader(username, password))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } catch (final IllegalArgumentException e) {
            return SvwsConnectionTestResult.failure("Die Base-URL ist technisch ungültig.");
        }

        final SvwsConnectionTestResult targetRejection = rejectDisallowedTarget(uri);
        if (targetRejection != null) {
            return targetRejection;
        }

        return send(requestBuilder, response -> {
            final int status = response.statusCode();
            if (status == 200) {
                return Boolean.parseBoolean(response.body().trim())
                    ? SvwsConnectionTestResult.success("Zugangsdaten gültig, privilegierter Zugriff bestätigt.")
                    : SvwsConnectionTestResult.failure("Zugangsdaten ungültig oder ohne privilegierten Zugriff.");
            }
            if (status == 401 || status == 403) {
                return SvwsConnectionTestResult.failure("Zugangsdaten ungültig oder ohne privilegierten Zugriff.");
            }
            return SvwsConnectionTestResult.failure("SVWS-Instanz antwortete mit Status " + status + ".");
        });
    }

    @Override
    public SvwsConnectionTestResult testReachability(final String baseUrl) {
        final URI uri;
        final HttpRequest.Builder requestBuilder;
        try {
            uri = URI.create(stripTrailingSlash(baseUrl) + ALIVE_PATH);
            requestBuilder = HttpRequest.newBuilder(uri).GET();
        } catch (final IllegalArgumentException e) {
            return SvwsConnectionTestResult.failure("Die Base-URL ist technisch ungültig.");
        }

        final SvwsConnectionTestResult targetRejection = rejectDisallowedTarget(uri);
        if (targetRejection != null) {
            return targetRejection;
        }

        return send(requestBuilder, response -> {
            final int status = response.statusCode();
            if (status == 200) {
                return SvwsConnectionTestResult.success(
                    "SVWS-Instanz erreichbar (keine Zugangsdaten hinterlegt, nur Basis-Erreichbarkeit geprüft).");
            }
            return SvwsConnectionTestResult.failure("SVWS-Instanz antwortete mit Status " + status + ".");
        });
    }

    /**
     * SSRF-Schutz (Issue #17): löst den Host der Ziel-URI auf und prüft ihn gegen
     * {@link #targetGuard}, bevor überhaupt ein Request gebaut/gesendet wird. Die Meldung bleibt
     * bewusst generisch - sie verrät weder die aufgelöste Adresse noch den Blockierungsgrund.
     */
    private SvwsConnectionTestResult rejectDisallowedTarget(final URI uri) {
        final String host = uri.getHost();
        if (host == null || !targetGuard.isAllowed(host)) {
            return SvwsConnectionTestResult.failure("Zielhost ist für Verbindungstests nicht zugelassen.");
        }
        return null;
    }

    private SvwsConnectionTestResult send(
            final HttpRequest.Builder requestBuilder,
            final Function<HttpResponse<String>, SvwsConnectionTestResult> classify) {
        final HttpRequest request = requestBuilder.timeout(requestTimeout).build();
        try {
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return classify.apply(response);
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

    private static String stripTrailingSlash(final String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static String basicAuthHeader(final String username, final String password) {
        final String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private static String jsonString(final String value) {
        final StringBuilder result = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            switch (c) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (c < 0x20) {
                        result.append(String.format("\\u%04x", (int) c));
                    } else {
                        result.append(c);
                    }
                }
            }
        }
        return result.append('"').toString();
    }
}
