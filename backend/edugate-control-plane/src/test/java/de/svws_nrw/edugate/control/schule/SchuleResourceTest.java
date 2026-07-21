package de.svws_nrw.edugate.control.schule;

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
 * CRUD-Happy-Path, Validierung, 401/403 und Cross-Tenant-Isolation für Schulen. Schule ist die
 * tenant-gebundene Zuordnungsstufe zwischen Schulträger und Schema (ADR-012); Struktur analog zu
 * {@code AnsprechpartnerResourceTest}.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SchuleResourceTest {

    private static final String SCHULTRAEGER_PATH = "/admin/api/v1/schultraeger";
    private static final String SVWS_INSTANZ_PATH = "/admin/api/v1/svws-instanzen";
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
            .body("aktiv", equalTo(true))
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

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void deaktivierenSetztAktivAufFalseUndErzeugtAuditEintrag() {
        final String schultraegerId = neuenSchultraegerAnlegen();
        final String path = schulenPath(schultraegerId);

        final String createBody = "{\"schulnummer\": \"111222\", \"name\": \"Zu deaktivierende Schule\"}";
        final String id = given().contentType(ContentType.JSON).body(createBody).when().post(path)
            .then().statusCode(201).extract().path("id");

        given()
            .when().delete(path + "/" + id)
            .then()
            .statusCode(200)
            .body("aktiv", equalTo(false));

        given().when().get(path + "/" + id).then().statusCode(200).body("aktiv", equalTo(false));

        assertThat(auditOutcomesFor(UUID.fromString(id), "SCHULE_DEACTIVATE")).containsExactly("SUCCESS");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void deaktivierenUnbekannterSchuleErgibt404() {
        final String path = schulenPath(neuenSchultraegerAnlegen());

        given()
            .when().delete(path + "/" + UUID.randomUUID())
            .then()
            .statusCode(404)
            .contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void deaktivierenUeberFremdenSchultraegerPfadWirdVerweigert() {
        final String schultraegerA = neuenSchultraegerAnlegen();
        final String schultraegerB = neuenSchultraegerAnlegen();

        final String createBody = "{\"schulnummer\": \"333444\", \"name\": \"Schule von A\"}";
        final String schuleId = given().contentType(ContentType.JSON).body(createBody)
            .when().post(schulenPath(schultraegerA)).then().statusCode(201).extract().path("id");

        given()
            .when().delete(schulenPath(schultraegerB) + "/" + schuleId)
            .then()
            .statusCode(404)
            .contentType("application/problem+json");

        given().when().get(schulenPath(schultraegerA) + "/" + schuleId).then().statusCode(200).body("aktiv", equalTo(true));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void endgueltigesLoeschenOhneSchemataEntferntSchule() {
        final String schultraegerId = neuenSchultraegerAnlegen();
        final String path = schulenPath(schultraegerId);
        final String createBody = "{\"schulnummer\": \"888881\", \"name\": \"Zu löschende Schule\"}";
        final String id = given().contentType(ContentType.JSON).body(createBody).when().post(path)
            .then().statusCode(201).extract().path("id");

        given().when().delete(path + "/" + id + "/endgueltig").then().statusCode(204);

        given().when().get(path + "/" + id).then().statusCode(404);
        assertThat(auditOutcomesFor(UUID.fromString(id), "SCHULE_DELETE")).containsExactly("SUCCESS");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void endgueltigesLoeschenEntferntAuchVorbereiteteSchemata() {
        final String schultraegerId = neuenSchultraegerAnlegen();
        final String path = schulenPath(schultraegerId);
        final String createBody = "{\"schulnummer\": \"888882\", \"name\": \"Schule mit geplantem Schema\"}";
        final String id = given().contentType(ContentType.JSON).body(createBody).when().post(path)
            .then().statusCode(201).extract().path("id");
        final String instanzId = neueSvwsInstanzAnlegen();
        geplantesSchemaAnlegen(schultraegerId, id, instanzId, "888882");

        given().when().delete(path + "/" + id + "/endgueltig").then().statusCode(204);

        given().when().get(path + "/" + id).then().statusCode(404);
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void endgueltigesLoeschenBlockiertBeiEchtemSchema() {
        final String schultraegerId = neuenSchultraegerAnlegen();
        final String path = schulenPath(schultraegerId);
        final String createBody = "{\"schulnummer\": \"888883\", \"name\": \"Schule mit echtem Schema\"}";
        final String id = given().contentType(ContentType.JSON).body(createBody).when().post(path)
            .then().statusCode(201).extract().path("id");
        final String instanzId = neueSvwsInstanzAnlegen();
        final String schemaId = geplantesSchemaAnlegen(schultraegerId, id, instanzId, "888883");
        schemaAufEchtenStatusHeben(schultraegerId, id, instanzId, schemaId, "888883");

        given()
            .when().delete(path + "/" + id + "/endgueltig")
            .then()
            .statusCode(409)
            .contentType("application/problem+json")
            .body("detail", org.hamcrest.Matchers.containsString("888883"));

        given().when().get(path + "/" + id).then().statusCode(200);
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void endgueltigesLoeschenUnbekannterSchuleErgibt404() {
        final String path = schulenPath(neuenSchultraegerAnlegen());

        given()
            .when().delete(path + "/" + UUID.randomUUID() + "/endgueltig")
            .then()
            .statusCode(404)
            .contentType("application/problem+json");
    }

    private static String neueSvwsInstanzAnlegen() {
        final String baseUrl = "https://svws-schule-test-" + UUID.randomUUID() + ".example.org";
        final String body = "{\"name\": \"Instanz-" + UUID.randomUUID() + "\", \"baseUrl\": \"" + baseUrl + "\"}";
        return given().contentType(ContentType.JSON).body(body).when().post(SVWS_INSTANZ_PATH)
            .then().statusCode(201).extract().path("id");
    }

    private static String geplantesSchemaAnlegen(final String schultraegerId, final String schuleId, final String instanzId,
            final String schemaName) {
        final String path = schemataPath(schultraegerId, schuleId);
        final String body = "{\"instanzId\": \"" + instanzId + "\", \"schemaName\": \"" + schemaName + "\", "
            + "\"umgebung\": \"TEST\"}";
        return given().contentType(ContentType.JSON).body(body).when().post(path).then().statusCode(201).extract().path("id");
    }

    private static void schemaAufEchtenStatusHeben(final String schultraegerId, final String schuleId, final String instanzId,
            final String schemaId, final String schemaName) {
        final String path = schemataPath(schultraegerId, schuleId);
        final String updateBody = "{\"instanzId\": \"" + instanzId + "\", \"schemaName\": \"" + schemaName + "\", "
            + "\"umgebung\": \"TEST\", \"status\": \"AKTIV\", \"aktiv\": true}";
        given().contentType(ContentType.JSON).body(updateBody).when().put(path + "/" + schemaId).then().statusCode(200);
    }

    private static String schemataPath(final String schultraegerId, final String schuleId) {
        return SCHULTRAEGER_PATH + "/" + schultraegerId + "/schulen/" + schuleId + "/schemata";
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
