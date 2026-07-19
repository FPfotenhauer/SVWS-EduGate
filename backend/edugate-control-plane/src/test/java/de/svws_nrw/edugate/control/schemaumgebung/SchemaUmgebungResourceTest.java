package de.svws_nrw.edugate.control.schemaumgebung;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;

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
 * CRUD-Happy-Path, Validierung, 401/403/404/409, Schutz der Systemumgebung "PRODUKTIV" (ADR-012)
 * und Audit für die Betreiber-Umgebungsverwaltung.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SchemaUmgebungResourceTest {

    private static final String PATH = "/admin/api/v1/schema-umgebungen";
    private static final String ADMIN_USER = "admin@edugate.local";

    @Test
    void ohneTokenGibtEs401AlsProblemJson() {
        given().when().get(PATH).then().statusCode(401).contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = "user-ohne-rolle", roles = {})
    void mitTokenOhneRolleGibtEs403AlsProblemJson() {
        given().when().get(PATH).then().statusCode(403).contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void listeEnthaeltDieStartwerteAusDerMigration() {
        given()
            .when().get(PATH)
            .then()
            .statusCode(200)
            .body("name", hasItem("PRODUKTIV"))
            .body("name", hasItem("TEST"))
            .body("name", hasItem("SCHULUNG"));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void crudHappyPath() {
        final String name = "ABNAHME_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
        final String createBody = "{\"name\": \"" + name + "\", \"beschreibung\": \"Abnahmeumgebung\"}";

        final String id = given()
            .contentType(ContentType.JSON)
            .body(createBody)
            .when().post(PATH)
            .then()
            .statusCode(201)
            .body("name", equalTo(name))
            .body("system", equalTo(false))
            .body("aktiv", equalTo(true))
            .body("beschreibung", equalTo("Abnahmeumgebung"))
            .extract().path("id");

        given().when().get(PATH).then().statusCode(200).body("id", hasItem(id));

        final String updateBody = "{\"name\": \"" + name + "\", \"beschreibung\": \"geändert\", \"aktiv\": true}";
        given()
            .contentType(ContentType.JSON)
            .body(updateBody)
            .when().put(PATH + "/" + id)
            .then()
            .statusCode(200)
            .body("beschreibung", equalTo("geändert"));

        given()
            .when().delete(PATH + "/" + id)
            .then()
            .statusCode(200)
            .body("aktiv", equalTo(false));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void nameWirdBeimAnlegenNormalisiert() {
        final String name = "kleinbuchstaben" + UUID.randomUUID().toString().substring(0, 8);
        final String createBody = "{\"name\": \"" + name + "\"}";

        given()
            .contentType(ContentType.JSON)
            .body(createBody)
            .when().post(PATH)
            .then()
            .statusCode(201)
            .body("name", equalTo(name.toUpperCase(java.util.Locale.ROOT)));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void leererNameErgibt400() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"name\": \"\"}")
            .when().post(PATH)
            .then()
            .statusCode(400)
            .contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void ungueltigesNamensformatErgibt400() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"name\": \"nicht erlaubt!\"}")
            .when().post(PATH)
            .then()
            .statusCode(400)
            .contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void doppelterNameErgibt409() {
        final String name = "DUP_" + UUID.randomUUID().toString().substring(0, 8);
        final String body = "{\"name\": \"" + name + "\"}";

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
    void unbekannteIdBeimUpdateErgibt404() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"name\": \"EGAL\", \"aktiv\": true}")
            .when().put(PATH + "/" + UUID.randomUUID())
            .then()
            .statusCode(404)
            .contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void produktivKannNichtUmbenanntWerden() {
        final String produktivId = idVon("PRODUKTIV");

        given()
            .contentType(ContentType.JSON)
            .body("{\"name\": \"ANDERS\", \"aktiv\": true}")
            .when().put(PATH + "/" + produktivId)
            .then()
            .statusCode(403)
            .contentType("application/problem+json");

        given().when().get(PATH).then().statusCode(200).body("name", hasItem("PRODUKTIV"));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void produktivKannNichtViaUpdateDeaktiviertWerden() {
        final String produktivId = idVon("PRODUKTIV");

        given()
            .contentType(ContentType.JSON)
            .body("{\"name\": \"PRODUKTIV\", \"aktiv\": false}")
            .when().put(PATH + "/" + produktivId)
            .then()
            .statusCode(403)
            .contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void produktivKannNichtDeaktiviertWerden() {
        final String produktivId = idVon("PRODUKTIV");

        given()
            .when().delete(PATH + "/" + produktivId)
            .then()
            .statusCode(403)
            .contentType("application/problem+json");

        given().when().get(PATH).then().statusCode(200).body("find { it.name == 'PRODUKTIV' }.aktiv", equalTo(true));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void jedeOperationErzeugtGenauEinenAuditEintrag() {
        final String name = "AUDIT_" + UUID.randomUUID().toString().substring(0, 8);
        final String createBody = "{\"name\": \"" + name + "\"}";

        final String id = given().contentType(ContentType.JSON).body(createBody).when().post(PATH)
            .then().statusCode(201).extract().path("id");

        final String updateBody = "{\"name\": \"" + name + "\", \"beschreibung\": \"x\", \"aktiv\": true}";
        given().contentType(ContentType.JSON).body(updateBody).when().put(PATH + "/" + id).then().statusCode(200);

        given().when().delete(PATH + "/" + id).then().statusCode(200);

        assertThat(auditOutcomesFor(UUID.fromString(id), "SCHEMA_UMGEBUNG_CREATE")).containsExactly("SUCCESS");
        assertThat(auditOutcomesFor(UUID.fromString(id), "SCHEMA_UMGEBUNG_UPDATE")).containsExactly("SUCCESS");
        assertThat(auditOutcomesFor(UUID.fromString(id), "SCHEMA_UMGEBUNG_DEACTIVATE")).containsExactly("SUCCESS");
    }

    private String idVon(final String name) {
        return given()
            .when().get(PATH)
            .then()
            .statusCode(200)
            .extract().path("find { it.name == '" + name + "' }.id");
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
}
