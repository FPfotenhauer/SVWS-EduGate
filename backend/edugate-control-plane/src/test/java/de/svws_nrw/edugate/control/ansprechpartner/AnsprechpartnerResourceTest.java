package de.svws_nrw.edugate.control.ansprechpartner;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import de.svws_nrw.edugate.control.support.PostgresTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * CRUD-Happy-Path, Validierung, 401/403 und - besonders wichtig - Cross-Tenant-Isolation für
 * Ansprechpartner. Ansprechpartner ist die erste tenant-gebundene Kind-Entität (ADR-009), die
 * über einen echten REST-Endpunkt läuft (statt nur über {@code CrossTenantNegativeTest} gegen
 * {@code TenantContext} direkt) - dieser Test ist daher der scharfe Nachweis, dass RLS plus die
 * explizite {@code tenant_id}-Prüfung in {@code AnsprechpartnerService} tatsächlich verhindern,
 * dass ein Schulträger auf Ansprechpartner eines anderen zugreifen kann.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class AnsprechpartnerResourceTest {

    private static final String SCHULTRAEGER_PATH = "/admin/api/v1/schultraeger";
    private static final String ADMIN_USER = "admin@edugate.local";

    @Test
    void ohneTokenGibtEs401AlsProblemJson() {
        given()
            .when().get(SCHULTRAEGER_PATH + "/" + UUID.randomUUID() + "/ansprechpartner")
            .then()
            .statusCode(401)
            .contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = "user-ohne-rolle", roles = {})
    void mitTokenOhneRolleGibtEs403AlsProblemJson() {
        given()
            .when().get(SCHULTRAEGER_PATH + "/" + UUID.randomUUID() + "/ansprechpartner")
            .then()
            .statusCode(403)
            .contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void crudHappyPath() {
        final String schultraegerId = neuenSchultraegerAnlegen();
        final String path = ansprechpartnerPath(schultraegerId);

        given().when().get(path).then().statusCode(200).body("size()", equalTo(0));

        final String createBody = "{\"name\": \"Mustermann\", \"vorname\": \"Max\", \"titel\": \"Dr.\", "
            + "\"abteilung\": \"IT\", \"funktion\": \"Leitung\", \"email\": \"max@example.org\", "
            + "\"telefonFestnetz\": \"0201 123456\", \"telefonMobil\": \"0170 1234567\", "
            + "\"beschreibung\": \"Erster Ansprechpartner\"}";

        final String id = given()
            .contentType(ContentType.JSON)
            .body(createBody)
            .when().post(path)
            .then()
            .statusCode(201)
            .body("name", equalTo("Mustermann"))
            .body("vorname", equalTo("Max"))
            .body("titel", equalTo("Dr."))
            .body("abteilung", equalTo("IT"))
            .body("funktion", equalTo("Leitung"))
            .body("email", equalTo("max@example.org"))
            .body("telefonFestnetz", equalTo("0201 123456"))
            .body("telefonMobil", equalTo("0170 1234567"))
            .body("beschreibung", equalTo("Erster Ansprechpartner"))
            .extract().path("id");

        given().when().get(path).then().statusCode(200).body("size()", equalTo(1)).body("[0].id", equalTo(id));

        final String updateBody = "{\"name\": \"Mustermann\", \"vorname\": \"Maxine\", \"titel\": \"\", "
            + "\"abteilung\": \"Verwaltung\", \"funktion\": \"\", \"email\": \"\", "
            + "\"telefonFestnetz\": \"\", \"telefonMobil\": \"\", \"beschreibung\": \"\"}";
        given()
            .contentType(ContentType.JSON)
            .body(updateBody)
            .when().put(path + "/" + id)
            .then()
            .statusCode(200)
            .body("vorname", equalTo("Maxine"))
            .body("abteilung", equalTo("Verwaltung"));

        given().when().delete(path + "/" + id).then().statusCode(204);

        given().when().get(path).then().statusCode(200).body("size()", equalTo(0));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void leererNameErgibt400() {
        final String path = ansprechpartnerPath(neuenSchultraegerAnlegen());
        final String body = "{\"name\": \"\", \"vorname\": \"Max\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().post(path)
            .then()
            .statusCode(400)
            .contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void leererVornameErgibt400() {
        final String path = ansprechpartnerPath(neuenSchultraegerAnlegen());
        final String body = "{\"name\": \"Mustermann\", \"vorname\": \"\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().post(path)
            .then()
            .statusCode(400)
            .contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void ungueltigeEmailErgibt400() {
        final String path = ansprechpartnerPath(neuenSchultraegerAnlegen());
        final String body = "{\"name\": \"Mustermann\", \"vorname\": \"Max\", \"email\": \"keine-email\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().post(path)
            .then()
            .statusCode(400)
            .contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void unbekannteIdBeimUpdateErgibt404() {
        final String path = ansprechpartnerPath(neuenSchultraegerAnlegen());
        final String body = "{\"name\": \"Mustermann\", \"vorname\": \"Max\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().put(path + "/" + UUID.randomUUID())
            .then()
            .statusCode(404)
            .contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void crossTenantZugriffAufAnsprechpartnerWirdVerweigert() {
        final String schultraegerA = neuenSchultraegerAnlegen();
        final String schultraegerB = neuenSchultraegerAnlegen();

        final String createBody = "{\"name\": \"Mustermann\", \"vorname\": \"Max\"}";
        final String ansprechpartnerId = given()
            .contentType(ContentType.JSON)
            .body(createBody)
            .when().post(ansprechpartnerPath(schultraegerA))
            .then()
            .statusCode(201)
            .extract().path("id");

        // Liste unter Schulträger B ist leer - der Ansprechpartner von A taucht dort nicht auf.
        given()
            .when().get(ansprechpartnerPath(schultraegerB))
            .then()
            .statusCode(200)
            .body("size()", equalTo(0));

        // Bearbeiten über den Pfad von Schulträger B liefert 404, obwohl die ID existiert.
        final String updateBody = "{\"name\": \"Mustermann\", \"vorname\": \"Manipuliert\"}";
        given()
            .contentType(ContentType.JSON)
            .body(updateBody)
            .when().put(ansprechpartnerPath(schultraegerB) + "/" + ansprechpartnerId)
            .then()
            .statusCode(404)
            .contentType("application/problem+json");

        // Löschen über den Pfad von Schulträger B liefert ebenfalls 404.
        given()
            .when().delete(ansprechpartnerPath(schultraegerB) + "/" + ansprechpartnerId)
            .then()
            .statusCode(404)
            .contentType("application/problem+json");

        // Der Ansprechpartner existiert unter seinem eigentlichen Schulträger A weiterhin.
        given()
            .when().get(ansprechpartnerPath(schultraegerA))
            .then()
            .statusCode(200)
            .body("size()", equalTo(1))
            .body("[0].vorname", equalTo("Max"));
    }

    private String ansprechpartnerPath(final String schultraegerId) {
        return SCHULTRAEGER_PATH + "/" + schultraegerId + "/ansprechpartner";
    }

    private String neuenSchultraegerAnlegen() {
        final String traegernummer = "AP-" + UUID.randomUUID();
        final String body = "{\"name\": \"Testträger\", \"traegernummer\": \"" + traegernummer + "\"}";
        return given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().post(SCHULTRAEGER_PATH)
            .then()
            .statusCode(201)
            .extract().path("id");
    }
}
