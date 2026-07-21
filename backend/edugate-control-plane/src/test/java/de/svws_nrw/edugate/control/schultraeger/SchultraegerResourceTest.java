package de.svws_nrw.edugate.control.schultraeger;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import de.svws_nrw.edugate.control.support.PostgresTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Schulträger-CRUD-Happy-Path, Validierung und 401/403 gemäß FIRSTPROMPT.md Abnahmekriterien.
 *
 * <p>Der 401-Fall (kein Token) läuft ohne echtes Keycloak: OIDC-Discovery ist deaktiviert
 * (application.properties), daher entscheidet die Security-Policy anonym-abgelehnter Requests
 * ohne Netzwerkaufruf. Authentifizierte Fälle laufen über {@code @TestSecurity}, das die
 * Token-Prüfung vollständig umgeht.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SchultraegerResourceTest {

    private static final String PATH = "/admin/api/v1/schultraeger";
    private static final String ADMIN_USER = "admin@edugate.local";

    @Test
    void ohneTokenGibtEs401AlsProblemJson() {
        given()
            .when().get(PATH)
            .then()
            .statusCode(401)
            .contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = "user-ohne-rolle", roles = {})
    void mitTokenOhneRolleGibtEs403AlsProblemJson() {
        given()
            .when().get(PATH)
            .then()
            .statusCode(403)
            .contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void crudHappyPath() {
        final String traegernummer = "T-" + UUID.randomUUID();
        final String createBody = "{\"name\": \"Musterstadt\", \"traegernummer\": \"" + traegernummer + "\"}";

        final String id = given()
            .contentType(ContentType.JSON)
            .body(createBody)
            .when().post(PATH)
            .then()
            .statusCode(201)
            .body("name", equalTo("Musterstadt"))
            .body("traegernummer", equalTo(traegernummer))
            .body("aktiv", equalTo(true))
            .extract().path("id");

        given()
            .queryParam("q", traegernummer)
            .when().get(PATH)
            .then()
            .statusCode(200)
            .body("items.size()", equalTo(1))
            .body("items[0].traegernummer", equalTo(traegernummer));

        given()
            .when().get(PATH + "/" + id)
            .then()
            .statusCode(200)
            .body("id", equalTo(id));

        final String updateBody = "{\"name\": \"Musterstadt (neu)\", \"traegernummer\": \"" + traegernummer + "\"}";
        given()
            .contentType(ContentType.JSON)
            .body(updateBody)
            .when().put(PATH + "/" + id)
            .then()
            .statusCode(200)
            .body("name", equalTo("Musterstadt (neu)"));

        given()
            .when().delete(PATH + "/" + id)
            .then()
            .statusCode(204);

        given()
            .when().get(PATH + "/" + id)
            .then()
            .statusCode(200)
            .body("aktiv", equalTo(false));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void reaktivierenSetztAktivWiederAufTrueUndErzeugtAuditEintrag() {
        final String traegernummer = "REAK-" + UUID.randomUUID();
        final String createBody = "{\"name\": \"Musterstadt\", \"traegernummer\": \"" + traegernummer + "\"}";

        final String id = given().contentType(ContentType.JSON).body(createBody).when().post(PATH)
            .then().statusCode(201).extract().path("id");

        given().when().delete(PATH + "/" + id).then().statusCode(204);
        given().when().get(PATH + "/" + id).then().statusCode(200).body("aktiv", equalTo(false));

        given()
            .when().post(PATH + "/" + id + "/reaktivieren")
            .then()
            .statusCode(200)
            .body("aktiv", equalTo(true));

        given().when().get(PATH + "/" + id).then().statusCode(200).body("aktiv", equalTo(true));

        assertThat(auditOutcomesFor(UUID.fromString(id), "SCHULTRAEGER_REACTIVATE")).containsExactly("SUCCESS");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void reaktivierenUnbekannterIdErgibt404() {
        given()
            .when().post(PATH + "/" + UUID.randomUUID() + "/reaktivieren")
            .then()
            .statusCode(404)
            .contentType("application/problem+json");
    }

    private List<String> auditOutcomesFor(final UUID entityId, final String action) {
        try (Connection connection = PostgresTestResource.openAdminConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT outcome FROM audit_admin WHERE entity_id = ? AND action = ?")) {
            statement.setObject(1, entityId);
            statement.setString(2, action);
            try (ResultSet resultSet = statement.executeQuery()) {
                final List<String> outcomes = new ArrayList<>();
                while (resultSet.next()) {
                    outcomes.add(resultSet.getString("outcome"));
                }
                return outcomes;
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void adressfelderUndBeschreibungWerdenGespeichertUndKoennenAktualisiertWerden() {
        final String traegernummer = "ADR-" + UUID.randomUUID();
        final String createBody = "{\"name\": \"Musterstadt\", \"traegernummer\": \"" + traegernummer + "\", "
            + "\"strasse\": \"Hauptstraße 1\", \"plz\": \"12345\", \"ort\": \"Musterstadt\", "
            + "\"beschreibung\": \"Testträger\"}";

        final String id = given()
            .contentType(ContentType.JSON)
            .body(createBody)
            .when().post(PATH)
            .then()
            .statusCode(201)
            .body("strasse", equalTo("Hauptstraße 1"))
            .body("plz", equalTo("12345"))
            .body("ort", equalTo("Musterstadt"))
            .body("beschreibung", equalTo("Testträger"))
            .extract().path("id");

        final String updateBody = "{\"name\": \"Musterstadt\", \"traegernummer\": \"" + traegernummer + "\", "
            + "\"strasse\": \"Nebenstraße 2\", \"plz\": \"54321\", \"ort\": \"Anderestadt\", "
            + "\"beschreibung\": \"Geändert\"}";
        given()
            .contentType(ContentType.JSON)
            .body(updateBody)
            .when().put(PATH + "/" + id)
            .then()
            .statusCode(200)
            .body("strasse", equalTo("Nebenstraße 2"))
            .body("plz", equalTo("54321"))
            .body("ort", equalTo("Anderestadt"))
            .body("beschreibung", equalTo("Geändert"));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void adressfelderSindOptional() {
        final String traegernummer = "OPT-" + UUID.randomUUID();
        final String createBody = "{\"name\": \"Musterstadt\", \"traegernummer\": \"" + traegernummer + "\"}";

        given()
            .contentType(ContentType.JSON)
            .body(createBody)
            .when().post(PATH)
            .then()
            .statusCode(201)
            .body("strasse", equalTo(null))
            .body("plz", equalTo(null))
            .body("ort", equalTo(null))
            .body("beschreibung", equalTo(null));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void leererNameErgibt400() {
        final String body = "{\"name\": \"\", \"traegernummer\": \"T-" + UUID.randomUUID() + "\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().post(PATH)
            .then()
            .statusCode(400)
            .contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void unbekannteIdErgibt404AlsProblemJson() {
        given()
            .when().get(PATH + "/" + UUID.randomUUID())
            .then()
            .statusCode(404)
            .contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void doppelteTraegernummerErgibt409() {
        final String traegernummer = "DUP-" + UUID.randomUUID();
        final String body = "{\"name\": \"Original\", \"traegernummer\": \"" + traegernummer + "\"}";

        given().contentType(ContentType.JSON).body(body).when().post(PATH).then().statusCode(201);

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().post(PATH)
            .then()
            .statusCode(409)
            .contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void ohneKatalogIdUndSonderfallIstDieQuelleManuell() {
        final String traegernummer = "MAN-" + UUID.randomUUID();
        final String body = "{\"name\": \"Musterstadt\", \"traegernummer\": \"" + traegernummer + "\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().post(PATH)
            .then()
            .statusCode(201)
            .body("quelle", equalTo("MANUELL"))
            .body("katalogId", equalTo(null))
            .body("sonderfallHinweis", equalTo(null));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void sonderfallSetztQuelleUndSpeichertDenHinweis() {
        final String traegernummer = "SON-" + UUID.randomUUID();
        final String body = "{\"name\": \"Sondertraeger\", \"traegernummer\": \"" + traegernummer + "\", "
            + "\"sonderfall\": true, \"sonderfallHinweis\": \"Kein Eintrag in der Schuldatei, Neugründung\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().post(PATH)
            .then()
            .statusCode(201)
            .body("quelle", equalTo("SONDERFALL"))
            .body("katalogId", equalTo(null))
            .body("sonderfallHinweis", equalTo("Kein Eintrag in der Schuldatei, Neugründung"));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void katalogIdSetztQuelleLandeslisteUndUeberschreibtSonderfall() {
        final UUID katalogId = insertKatalogEintrag("KAT-" + UUID.randomUUID(), "Katalogträger");
        final String traegernummer = "LL-" + UUID.randomUUID();
        final String body = "{\"name\": \"Katalogträger\", \"traegernummer\": \"" + traegernummer + "\", "
            + "\"katalogId\": \"" + katalogId + "\", \"sonderfall\": true, \"sonderfallHinweis\": \"wird ignoriert\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().post(PATH)
            .then()
            .statusCode(201)
            .body("quelle", equalTo("LANDESLISTE"))
            .body("katalogId", equalTo(katalogId.toString()))
            .body("sonderfallHinweis", equalTo(null));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void unbekannteKatalogIdErgibt404() {
        final String traegernummer = "UNK-" + UUID.randomUUID();
        final String body = "{\"name\": \"Musterstadt\", \"traegernummer\": \"" + traegernummer + "\", "
            + "\"katalogId\": \"" + UUID.randomUUID() + "\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().post(PATH)
            .then()
            .statusCode(404)
            .contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void endgueltigesLoeschenEntferntManuellenSchultraegerOhneSchulen() {
        final String traegernummer = "DEL-" + UUID.randomUUID();
        final String body = "{\"name\": \"Zu löschen\", \"traegernummer\": \"" + traegernummer + "\"}";
        final String id = given().contentType(ContentType.JSON).body(body).when().post(PATH)
            .then().statusCode(201).extract().path("id");

        given()
            .when().delete(PATH + "/" + id + "/endgueltig")
            .then()
            .statusCode(204);

        given().when().get(PATH + "/" + id).then().statusCode(404);
        assertThat(auditOutcomesFor(UUID.fromString(id), "SCHULTRAEGER_DELETE")).containsExactly("SUCCESS");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void endgueltigesLoeschenEntferntAuchSonderfallSchultraeger() {
        final String traegernummer = "DELSON-" + UUID.randomUUID();
        final String body = "{\"name\": \"Sonderfall\", \"traegernummer\": \"" + traegernummer + "\", "
            + "\"sonderfall\": true, \"sonderfallHinweis\": \"Testfall\"}";
        final String id = given().contentType(ContentType.JSON).body(body).when().post(PATH)
            .then().statusCode(201).extract().path("id");

        given().when().delete(PATH + "/" + id + "/endgueltig").then().statusCode(204);
        given().when().get(PATH + "/" + id).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void endgueltigesLoeschenEntferntAuchAbhaengigeAnsprechpartner() {
        final String traegernummer = "DELAP-" + UUID.randomUUID();
        final String body = "{\"name\": \"Mit Ansprechpartner\", \"traegernummer\": \"" + traegernummer + "\"}";
        final String id = given().contentType(ContentType.JSON).body(body).when().post(PATH)
            .then().statusCode(201).extract().path("id");
        insertAnsprechpartner(UUID.fromString(id));

        given().when().delete(PATH + "/" + id + "/endgueltig").then().statusCode(204);

        assertThat(countAnsprechpartner(UUID.fromString(id))).isZero();
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void endgueltigesLoeschenBlockiertBeiVorhandenenSchulen() {
        final String traegernummer = "DELSCH-" + UUID.randomUUID();
        final String body = "{\"name\": \"Mit Schule\", \"traegernummer\": \"" + traegernummer + "\"}";
        final String id = given().contentType(ContentType.JSON).body(body).when().post(PATH)
            .then().statusCode(201).extract().path("id");
        insertSchule(UUID.fromString(id));

        given()
            .when().delete(PATH + "/" + id + "/endgueltig")
            .then()
            .statusCode(409)
            .contentType("application/problem+json")
            .body("detail", org.hamcrest.Matchers.containsString("1 Schule"));

        given().when().get(PATH + "/" + id).then().statusCode(200);
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void endgueltigesLoeschenBlockiertBeiLandeslistenSchultraeger() {
        final UUID katalogId = insertKatalogEintrag("LLDEL-" + UUID.randomUUID(), "Landeslisten-Träger");
        final String traegernummer = "DELLL-" + UUID.randomUUID();
        final String body = "{\"name\": \"Landeslisten-Träger\", \"traegernummer\": \"" + traegernummer + "\", "
            + "\"katalogId\": \"" + katalogId + "\"}";
        final String id = given().contentType(ContentType.JSON).body(body).when().post(PATH)
            .then().statusCode(201).extract().path("id");

        given()
            .when().delete(PATH + "/" + id + "/endgueltig")
            .then()
            .statusCode(409)
            .contentType("application/problem+json");

        given().when().get(PATH + "/" + id).then().statusCode(200);
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void endgueltigesLoeschenUnbekannterIdErgibt404() {
        given()
            .when().delete(PATH + "/" + UUID.randomUUID() + "/endgueltig")
            .then()
            .statusCode(404)
            .contentType("application/problem+json");
    }

    private void insertSchule(final UUID tenantId) {
        try (Connection connection = PostgresTestResource.openAdminConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO schule (tenant_id, schulnummer, name) VALUES (?, ?, ?)")) {
            statement.setObject(1, tenantId);
            statement.setString(2, "SCH-" + UUID.randomUUID());
            statement.setString(3, "Testschule");
            statement.executeUpdate();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void insertAnsprechpartner(final UUID tenantId) {
        try (Connection connection = PostgresTestResource.openAdminConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO ansprechpartner (tenant_id, name, vorname) VALUES (?, ?, ?)")) {
            statement.setObject(1, tenantId);
            statement.setString(2, "Muster");
            statement.setString(3, "Max");
            statement.executeUpdate();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private long countAnsprechpartner(final UUID tenantId) {
        try (Connection connection = PostgresTestResource.openAdminConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT count(*) FROM ansprechpartner WHERE tenant_id = ?")) {
            statement.setObject(1, tenantId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private UUID insertKatalogEintrag(final String traegernummer, final String traegername) {
        try (Connection connection = PostgresTestResource.openAdminConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO schultraeger_katalog (bundeslandkennung, traegernummer, traegername) "
                     + "VALUES ('NRW', ?, ?) RETURNING id")) {
            statement.setString(1, traegernummer);
            statement.setString(2, traegername);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return (UUID) resultSet.getObject("id");
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }
}
