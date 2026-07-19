package de.svws_nrw.edugate.control.schule;

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
 * CRUD-Happy-Path, Validierung, 401/403 und Cross-Tenant-Isolation für Schulen. Schule ist die
 * tenant-gebundene Zuordnungsstufe zwischen Schulträger und Schema (ADR-012); Struktur analog zu
 * {@code AnsprechpartnerResourceTest}.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SchuleResourceTest {

    private static final String SCHULTRAEGER_PATH = "/admin/api/v1/schultraeger";
    private static final String ADMIN_USER = "admin@edugate.local";

    @Test
    void ohneTokenGibtEs401AlsProblemJson() {
        given()
            .when().get(SCHULTRAEGER_PATH + "/" + UUID.randomUUID() + "/schulen")
            .then()
            .statusCode(401)
            .contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = "user-ohne-rolle", roles = {})
    void mitTokenOhneRolleGibtEs403AlsProblemJson() {
        given()
            .when().get(SCHULTRAEGER_PATH + "/" + UUID.randomUUID() + "/schulen")
            .then()
            .statusCode(403)
            .contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void crudHappyPath() {
        final String schultraegerId = neuenSchultraegerAnlegen();
        final String path = schulenPath(schultraegerId);

        given().when().get(path).then().statusCode(200).body("size()", equalTo(0));

        final String createBody = "{\"schulnummer\": \"123456\", \"name\": \"Musterschule\"}";
        final String id = given()
            .contentType(ContentType.JSON)
            .body(createBody)
            .when().post(path)
            .then()
            .statusCode(201)
            .body("schulnummer", equalTo("123456"))
            .body("name", equalTo("Musterschule"))
            .extract().path("id");

        given().when().get(path + "/" + id).then().statusCode(200).body("id", equalTo(id));

        final String updateBody = "{\"schulnummer\": \"123456\", \"name\": \"Musterschule (neu)\"}";
        given()
            .contentType(ContentType.JSON)
            .body(updateBody)
            .when().put(path + "/" + id)
            .then()
            .statusCode(200)
            .body("name", equalTo("Musterschule (neu)"));

        given().when().get(path).then().statusCode(200).body("size()", equalTo(1)).body("[0].id", equalTo(id));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void leereSchulnummerErgibt400() {
        final String path = schulenPath(neuenSchultraegerAnlegen());
        final String body = "{\"schulnummer\": \"\", \"name\": \"Musterschule\"}";

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
        final String path = schulenPath(neuenSchultraegerAnlegen());
        final String body = "{\"schulnummer\": \"123456\", \"name\": \"Musterschule\"}";

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
    void crossTenantZugriffAufSchuleWirdVerweigert() {
        final String schultraegerA = neuenSchultraegerAnlegen();
        final String schultraegerB = neuenSchultraegerAnlegen();

        final String createBody = "{\"schulnummer\": \"654321\", \"name\": \"Schule von A\"}";
        final String schuleId = given()
            .contentType(ContentType.JSON)
            .body(createBody)
            .when().post(schulenPath(schultraegerA))
            .then()
            .statusCode(201)
            .extract().path("id");

        given()
            .when().get(schulenPath(schultraegerB))
            .then()
            .statusCode(200)
            .body("size()", equalTo(0));

        given()
            .when().get(schulenPath(schultraegerB) + "/" + schuleId)
            .then()
            .statusCode(404)
            .contentType("application/problem+json");

        final String updateBody = "{\"schulnummer\": \"654321\", \"name\": \"Manipuliert\"}";
        given()
            .contentType(ContentType.JSON)
            .body(updateBody)
            .when().put(schulenPath(schultraegerB) + "/" + schuleId)
            .then()
            .statusCode(404)
            .contentType("application/problem+json");
    }

    static String schulenPath(final String schultraegerId) {
        return SCHULTRAEGER_PATH + "/" + schultraegerId + "/schulen";
    }

    static String neuenSchultraegerAnlegen() {
        final String traegernummer = "SC-" + UUID.randomUUID();
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
