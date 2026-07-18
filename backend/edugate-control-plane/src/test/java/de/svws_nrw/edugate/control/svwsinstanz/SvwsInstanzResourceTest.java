package de.svws_nrw.edugate.control.svwsinstanz;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import de.svws_nrw.edugate.control.support.PostgresTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * SVWS-Instanz-CRUD-Happy-Path, Validierung, 401/403, Credentials-Handling und Audit gemäß der
 * SVWS-Serververwaltung (ADR-011). Struktur analog zu {@code SchultraegerResourceTest}
 * (erster vertikaler Durchstich).
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SvwsInstanzResourceTest {

    private static final String PATH = "/admin/api/v1/svws-instanzen";
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
        final String baseUrl = "https://svws-" + UUID.randomUUID() + ".example.org";
        final String createBody = "{\"name\": \"Testinstanz\", \"baseUrl\": \"" + baseUrl + "\"}";

        final String id = given()
            .contentType(ContentType.JSON)
            .body(createBody)
            .when().post(PATH)
            .then()
            .statusCode(201)
            .body("name", equalTo("Testinstanz"))
            .body("baseUrl", equalTo(baseUrl))
            .body("status", equalTo("OK"))
            .body("aktiv", equalTo(true))
            .body("credentialsHinterlegt", equalTo(false))
            .body("id", notNullValue())
            .extract().path("id");

        given()
            .queryParam("q", baseUrl)
            .when().get(PATH)
            .then()
            .statusCode(200)
            .body("items.size()", equalTo(1))
            .body("items[0].baseUrl", equalTo(baseUrl));

        given()
            .when().get(PATH + "/" + id)
            .then()
            .statusCode(200)
            .body("id", equalTo(id));

        final String neueBaseUrl = "https://svws-neu-" + UUID.randomUUID() + ".example.org";
        final String updateBody = "{\"name\": \"Testinstanz (neu)\", \"baseUrl\": \"" + neueBaseUrl + "\", "
            + "\"status\": \"DEGRADED\", \"aktiv\": true}";
        given()
            .contentType(ContentType.JSON)
            .body(updateBody)
            .when().put(PATH + "/" + id)
            .then()
            .statusCode(200)
            .body("name", equalTo("Testinstanz (neu)"))
            .body("baseUrl", equalTo(neueBaseUrl))
            .body("status", equalTo("DEGRADED"));

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
    void leererNameErgibt400() {
        final String body = "{\"name\": \"\", \"baseUrl\": \"https://svws-" + UUID.randomUUID() + ".example.org\"}";

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
    void leereBaseUrlErgibt400() {
        final String body = "{\"name\": \"Ohne Base-URL\", \"baseUrl\": \"\"}";

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
    void ungueltigeBaseUrlErgibt400() {
        final String body = "{\"name\": \"Ungültige URL\", \"baseUrl\": \"keine-url\"}";

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
    void doppelteBaseUrlErgibt409() {
        final String baseUrl = "https://svws-dup-" + UUID.randomUUID() + ".example.org";
        final String body = "{\"name\": \"Original\", \"baseUrl\": \"" + baseUrl + "\"}";

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
    void credentialsWerdenVerschluesseltGespeichertUndNieImResponseZurueckgegeben() throws Exception {
        final String baseUrl = "https://svws-cred-" + UUID.randomUUID() + ".example.org";
        final String createBody = "{\"name\": \"Mit Zugangsdaten\", \"baseUrl\": \"" + baseUrl + "\"}";

        final String id = given()
            .contentType(ContentType.JSON)
            .body(createBody)
            .when().post(PATH)
            .then()
            .statusCode(201)
            .extract().path("id");

        final String credentialsBody = "{\"username\": \"svws-admin\", \"password\": \"geheimes-passwort\"}";
        given()
            .contentType(ContentType.JSON)
            .body(credentialsBody)
            .when().put(PATH + "/" + id + "/credentials")
            .then()
            .statusCode(204);

        given()
            .when().get(PATH + "/" + id)
            .then()
            .statusCode(200)
            .body("credentialsHinterlegt", equalTo(true))
            .body("keySet()", not(hasItem("credentialsEncrypted")))
            .body("keySet()", not(hasItem("password")));

        try (Connection connection = PostgresTestResource.openAdminConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT credentials_encrypted FROM svws_instanz WHERE id = ?")) {
            statement.setObject(1, UUID.fromString(id));
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                final byte[] stored = resultSet.getBytes("credentials_encrypted");
                assertThat(stored).isNotNull();
                final String storedAsText = new String(stored, StandardCharsets.ISO_8859_1);
                assertThat(storedAsText)
                    .as("Zugangsdaten dürfen nicht im Klartext in der Datenbank liegen")
                    .doesNotContain("geheimes-passwort")
                    .doesNotContain("svws-admin");
            }
        }
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void credentialsFuerUnbekannteInstanzErgibt404() {
        final String body = "{\"username\": \"a\", \"password\": \"b\"}";

        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().put(PATH + "/" + UUID.randomUUID() + "/credentials")
            .then()
            .statusCode(404)
            .contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void jedeOperationErzeugtGenauEinenAuditEintrag() throws Exception {
        final String baseUrl = "https://svws-audit-" + UUID.randomUUID() + ".example.org";
        final String createBody = "{\"name\": \"Audit-Test\", \"baseUrl\": \"" + baseUrl + "\"}";

        final String id = given()
            .contentType(ContentType.JSON)
            .body(createBody)
            .when().post(PATH)
            .then()
            .statusCode(201)
            .extract().path("id");

        given().when().get(PATH + "/" + id).then().statusCode(200);

        final String updateBody = "{\"name\": \"Audit-Test\", \"baseUrl\": \"" + baseUrl + "\", "
            + "\"status\": \"OK\", \"aktiv\": true}";
        given().contentType(ContentType.JSON).body(updateBody).when().put(PATH + "/" + id).then().statusCode(200);

        given().when().delete(PATH + "/" + id).then().statusCode(204);

        assertThat(auditOutcomesFor(UUID.fromString(id), "SVWS_INSTANZ_CREATE")).containsExactly("SUCCESS");
        assertThat(auditOutcomesFor(UUID.fromString(id), "SVWS_INSTANZ_READ")).containsExactly("SUCCESS");
        assertThat(auditOutcomesFor(UUID.fromString(id), "SVWS_INSTANZ_UPDATE")).containsExactly("SUCCESS");
        assertThat(auditOutcomesFor(UUID.fromString(id), "SVWS_INSTANZ_DEACTIVATE")).containsExactly("SUCCESS");
    }

    private List<String> auditOutcomesFor(final UUID entityId, final String action) throws Exception {
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
        }
    }
}
