package de.svws_nrw.edugate.core.svws;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;

/**
 * HTTP-Implementierung des {@link SvwsPrivilegedApiClient}-Ports (ADR-014).
 *
 * <p>{@link #listSchemas(String, String, String)} ruft {@code GET /api/schema/liste/svws} auf
 * (siehe {@code docs/entwicklung/svws-server-api.md}, {@code examples/open-api-privileged.json}
 * {@code SchemaListeEintrag}). Zugangsdaten werden als HTTP-Basic-Auth übertragen - der
 * SVWS-Server erzwingt Basic-Auth generisch auf {@code /api/schema/…} (siehe
 * {@link HttpSvwsConnectionTester}); zusätzlich benötigt dieser konkrete Endpunkt root-Rechte auf
 * der Datenbank und liefert sonst 403.
 *
 * <p>Netzwerk-, Protokoll- und Parsingfehler werden nie als Exception nach außen gereicht,
 * sondern immer in ein sicheres {@link SvwsSchemaListResult#failure(String)} übersetzt - siehe
 * Klassenkommentar von {@link HttpSvwsConnectionTester} für die Begründung (ADR-006).
 */
public final class HttpSvwsPrivilegedApiClient implements SvwsPrivilegedApiClient {

    private static final String SCHEMA_LISTE_SVWS_PATH = "/api/schema/liste/svws";

    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final SvwsTargetGuard targetGuard;

    /** Verwendet {@link SvwsTargetGuard#defaultDeny()} - sicherster Default ohne weitere Konfiguration. */
    public HttpSvwsPrivilegedApiClient() {
        this(Duration.ofSeconds(5), Duration.ofSeconds(15), null, SvwsTargetGuard.defaultDeny());
    }

    /**
     * @param sslContext optional - nur für lokale Entwicklung mit einem
     *      {@link DevTrustStoreSslContext}, um selbstsignierte Test-SVWS-Instanzen additiv zu
     *      vertrauen. {@code null} verwendet die Standard-JVM-Vertrauenskette (Produktionsfall).
     * @param targetGuard SSRF-Schutz (Issue #17) - entscheidet, gegen welche aufgelösten
     *      Zieladressen Aufrufe überhaupt zugelassen werden.
     */
    public HttpSvwsPrivilegedApiClient(final SSLContext sslContext, final SvwsTargetGuard targetGuard) {
        this(Duration.ofSeconds(5), Duration.ofSeconds(15), sslContext, targetGuard);
    }

    HttpSvwsPrivilegedApiClient(final Duration connectTimeout, final Duration requestTimeout) {
        this(connectTimeout, requestTimeout, null, SvwsTargetGuard.allowAll());
    }

    HttpSvwsPrivilegedApiClient(
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
    public SvwsSchemaListResult listSchemas(final String baseUrl, final String username, final String password) {
        final URI uri;
        final HttpRequest.Builder requestBuilder;
        try {
            uri = URI.create(stripTrailingSlash(baseUrl) + SCHEMA_LISTE_SVWS_PATH);
            requestBuilder = HttpRequest.newBuilder(uri).header("Authorization", basicAuthHeader(username, password)).GET();
        } catch (final IllegalArgumentException e) {
            return SvwsSchemaListResult.failure("Die Base-URL ist technisch ungültig.");
        }

        final String host = uri.getHost();
        if (host == null || !targetGuard.isAllowed(host)) {
            return SvwsSchemaListResult.failure("Zielhost ist für diese Operation nicht zugelassen.");
        }

        return send(requestBuilder, response -> {
            final int status = response.statusCode();
            if (status == 200) {
                return parseEntries(response.body());
            }
            if (status == 401 || status == 403) {
                return SvwsSchemaListResult.failure("Zugangsdaten ungültig oder ohne privilegierten Zugriff.");
            }
            return SvwsSchemaListResult.failure("SVWS-Instanz antwortete mit Status " + status + ".");
        });
    }

    private SvwsSchemaListResult parseEntries(final String body) {
        final Object parsed;
        try {
            parsed = MinimalJsonParser.parse(body);
        } catch (final JsonParseException e) {
            return SvwsSchemaListResult.failure("Antwort der SVWS-Instanz konnte nicht gelesen werden.");
        }
        if (!(parsed instanceof List<?> rawList)) {
            return SvwsSchemaListResult.failure("Antwort der SVWS-Instanz hatte ein unerwartetes Format.");
        }
        final List<SvwsSchemaListeEintrag> entries = new ArrayList<>();
        for (final Object rawEntry : rawList) {
            if (!(rawEntry instanceof Map<?, ?> map)) {
                return SvwsSchemaListResult.failure("Antwort der SVWS-Instanz hatte ein unerwartetes Format.");
            }
            entries.add(toEintrag(map));
        }
        return SvwsSchemaListResult.success(entries);
    }

    private SvwsSchemaListeEintrag toEintrag(final Map<?, ?> map) {
        return new SvwsSchemaListeEintrag(
            stringField(map, "name"),
            stringField(map, "username"),
            booleanField(map, "isSVWS"),
            longField(map, "revision"),
            booleanField(map, "isTainted"),
            booleanField(map, "isInConfig"),
            booleanField(map, "isDeactivated"));
    }

    private String stringField(final Map<?, ?> map, final String key) {
        final Object value = map.get(key);
        return value instanceof String s ? s : null;
    }

    private Boolean booleanField(final Map<?, ?> map, final String key) {
        final Object value = map.get(key);
        return value instanceof Boolean b ? b : null;
    }

    private Long longField(final Map<?, ?> map, final String key) {
        final Object value = map.get(key);
        return value instanceof Double d ? d.longValue() : null;
    }

    private SvwsSchemaListResult send(
            final HttpRequest.Builder requestBuilder,
            final Function<HttpResponse<String>, SvwsSchemaListResult> classify) {
        final HttpRequest request = requestBuilder.timeout(requestTimeout).build();
        try {
            final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return classify.apply(response);
        } catch (final HttpTimeoutException e) {
            return SvwsSchemaListResult.failure("Zeitüberschreitung beim Verbindungsaufbau.");
        } catch (final SSLException e) {
            return SvwsSchemaListResult.failure("TLS-/Zertifikatsfehler bei der Verbindung.");
        } catch (final IOException e) {
            return SvwsSchemaListResult.failure("Verbindung zur SVWS-Instanz war nicht möglich (Netzwerkfehler).");
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return SvwsSchemaListResult.failure("Anfrage wurde unterbrochen.");
        }
    }

    private static String stripTrailingSlash(final String baseUrl) {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private static String basicAuthHeader(final String username, final String password) {
        final String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }
}
