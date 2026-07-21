package de.svws_nrw.edugate.control.schema;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.svws_nrw.edugate.control.support.PostgresTestResource;
import de.svws_nrw.edugate.core.svws.SvwsPrivilegedApiClient;
import de.svws_nrw.edugate.core.svws.SvwsSchemaDestroyResult;
import io.quarkus.test.InjectMock;
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
 * CRUD-Happy-Path, Namensvorschlag, Validierung, Eindeutigkeits-/Produktiv-Regeln, Cross-Tenant-
 * Isolation und Audit für Schemata/Schuldatenbanken (ADR-012).
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SchemaResourceTest {

    private static final String SCHULTRAEGER_PATH = "/admin/api/v1/schultraeger";
    private static final String SVWS_INSTANZ_PATH = "/admin/api/v1/svws-instanzen";
    private static final String ADMIN_USER = "admin@edugate.local";

    @InjectMock
    SvwsPrivilegedApiClient privilegedApiClient;

    @Test
    void ohneTokenGibtEs401AlsProblemJson() {
        given()
            .when().get(schemataPath(UUID.randomUUID().toString(), UUID.randomUUID().toString()))
            .then()
            .statusCode(401)
            .contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = "user-ohne-rolle", roles = {})
    void mitTokenOhneRolleGibtEs403AlsProblemJson() {
        given()
            .when().get(schemataPath(UUID.randomUUID().toString(), UUID.randomUUID().toString()))
            .then()
            .statusCode(403)
            .contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void namensvorschlagFuerBelastbareSchulnummerFolgtDerStandardkonvention() {
        final String schultraegerId = neuenSchultraegerAnlegen();
        final String schuleId = neueSchuleAnlegen(schultraegerId, "123456", "Musterschule");

        given()
            .queryParam("umgebung", "PRODUKTIV")
            .when().get(schemataPath(schultraegerId, schuleId) + "/namensvorschlag")
            .then()
            .statusCode(200)
            .body("schemaName", equalTo("123456"))
            .body("belastbareSchulnummer", equalTo(true));

        given()
            .queryParam("umgebung", "test")
            .when().get(schemataPath(schultraegerId, schuleId) + "/namensvorschlag")
            .then()
            .statusCode(200)
            .body("schemaName", equalTo("123456_test"))
            .body("belastbareSchulnummer", equalTo(true));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void namensvorschlagOhneBelastbareSchulnummerLiefertKeinenVorschlag() {
        final String schultraegerId = neuenSchultraegerAnlegen();
        final String schuleId = neueSchuleAnlegen(schultraegerId, "vorlaeufig-ohne-nummer", "Schule ohne Nummer");

        given()
            .queryParam("umgebung", "PRODUKTIV")
            .when().get(schemataPath(schultraegerId, schuleId) + "/namensvorschlag")
            .then()
            .statusCode(200)
            .body("schemaName", nullValue())
            .body("belastbareSchulnummer", equalTo(false));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void crudHappyPath() {
        final String schultraegerId = neuenSchultraegerAnlegen();
        final String schuleId = neueSchuleAnlegen(schultraegerId, "111111", "Schule Eins");
        final String instanzId = neueSvwsInstanzAnlegen();
        final String path = schemataPath(schultraegerId, schuleId);

        given().when().get(path).then().statusCode(200).body("size()", equalTo(0));

        final String createBody = "{\"instanzId\": \"" + instanzId + "\", \"schemaName\": \"111111\", "
            + "\"umgebung\": \"produktiv\", \"beschreibung\": \"Produktivdatenbank\"}";
        final String id = given()
            .contentType(ContentType.JSON)
            .body(createBody)
            .when().post(path)
            .then()
            .statusCode(201)
            .body("schemaName", equalTo("111111"))
            .body("umgebung", equalTo("PRODUKTIV"))
            .body("status", equalTo("GEPLANT"))
            .body("aktiv", equalTo(true))
            .body("source", equalTo("MANUELL"))
            .body("beschreibung", equalTo("Produktivdatenbank"))
            .extract().path("id");

        given().when().get(path).then().statusCode(200).body("size()", equalTo(1)).body("[0].id", equalTo(id));

        final String updateBody = "{\"instanzId\": \"" + instanzId + "\", \"schemaName\": \"111111\", "
            + "\"umgebung\": \"PRODUKTIV\", \"beschreibung\": \"geändert\", \"status\": \"AKTIV\", \"aktiv\": true}";
        given()
            .contentType(ContentType.JSON)
            .body(updateBody)
            .when().put(path + "/" + id)
            .then()
            .statusCode(200)
            .body("status", equalTo("AKTIV"))
            .body("beschreibung", equalTo("geändert"));

        given()
            .when().delete(path + "/" + id)
            .then()
            .statusCode(200)
            .body("aktiv", equalTo(false))
            .body("status", equalTo("DEAKTIVIERT"));

        assertThat(auditOutcomesFor(UUID.fromString(id), "SCHEMA_CREATE")).containsExactly("SUCCESS");
        assertThat(auditOutcomesFor(UUID.fromString(id), "SCHEMA_UPDATE")).containsExactly("SUCCESS");
        assertThat(auditOutcomesFor(UUID.fromString(id), "SCHEMA_DEACTIVATE")).containsExactly("SUCCESS");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void leererSchemaNameErgibt400() {
        final String schultraegerId = neuenSchultraegerAnlegen();
        final String schuleId = neueSchuleAnlegen(schultraegerId, "222222", "Schule Zwei");
        final String instanzId = neueSvwsInstanzAnlegen();

        final String body = "{\"instanzId\": \"" + instanzId + "\", \"schemaName\": \"\", \"umgebung\": \"TEST\"}";
        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().post(schemataPath(schultraegerId, schuleId))
            .then()
            .statusCode(400)
            .contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void ungueltigesSchemaNamensformatErgibt400() {
        final String schultraegerId = neuenSchultraegerAnlegen();
        final String schuleId = neueSchuleAnlegen(schultraegerId, "222223", "Schule Zwei B");
        final String instanzId = neueSvwsInstanzAnlegen();

        final String body = "{\"instanzId\": \"" + instanzId + "\", \"schemaName\": \"1nicht-erlaubt\", \"umgebung\": \"TEST\"}";
        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().post(schemataPath(schultraegerId, schuleId))
            .then()
            .statusCode(400)
            .contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void unbekannteInstanzErgibt404() {
        final String schultraegerId = neuenSchultraegerAnlegen();
        final String schuleId = neueSchuleAnlegen(schultraegerId, "333333", "Schule Drei");

        final String body = "{\"instanzId\": \"" + UUID.randomUUID() + "\", \"schemaName\": \"333333\", \"umgebung\": \"PRODUKTIV\"}";
        given()
            .contentType(ContentType.JSON)
            .body(body)
            .when().post(schemataPath(schultraegerId, schuleId))
            .then()
            .statusCode(404)
            .contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void doppelterSchemaNameAufDerselbenInstanzErgibt409() {
        final String schultraegerId = neuenSchultraegerAnlegen();
        final String instanzId = neueSvwsInstanzAnlegen();
        final String schuleA = neueSchuleAnlegen(schultraegerId, "444441", "Schule Vier A");
        final String schuleB = neueSchuleAnlegen(schultraegerId, "444442", "Schule Vier B");

        final String sharedName = "gemeinsamer_name_" + UUID.randomUUID().toString().substring(0, 8).replace("-", "a");
        final String bodyA = "{\"instanzId\": \"" + instanzId + "\", \"schemaName\": \"" + sharedName + "\", \"umgebung\": \"TEST\"}";
        given().contentType(ContentType.JSON).body(bodyA).when().post(schemataPath(schultraegerId, schuleA))
            .then().statusCode(201);

        given()
            .contentType(ContentType.JSON)
            .body(bodyA)
            .when().post(schemataPath(schultraegerId, schuleB))
            .then()
            .statusCode(409)
            .contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void hoechstensEinAktivesProduktivSchemaProSchule() {
        final String schultraegerId = neuenSchultraegerAnlegen();
        final String schuleId = neueSchuleAnlegen(schultraegerId, "555555", "Schule Fünf");
        final String instanzId = neueSvwsInstanzAnlegen();
        final String path = schemataPath(schultraegerId, schuleId);

        final String erstesBody = "{\"instanzId\": \"" + instanzId + "\", \"schemaName\": \"555555\", \"umgebung\": \"PRODUKTIV\"}";
        final String erstesId = given().contentType(ContentType.JSON).body(erstesBody).when().post(path)
            .then().statusCode(201).extract().path("id");

        final String zweitesBody = "{\"instanzId\": \"" + instanzId + "\", \"schemaName\": \"555555_zwei\", \"umgebung\": \"PRODUKTIV\"}";
        given()
            .contentType(ContentType.JSON)
            .body(zweitesBody)
            .when().post(path)
            .then()
            .statusCode(409)
            .contentType("application/problem+json");

        // Weitere Nicht-Produktiv-Umgebungen bleiben uneingeschränkt möglich.
        final String testBody = "{\"instanzId\": \"" + instanzId + "\", \"schemaName\": \"555555_test\", \"umgebung\": \"TEST\"}";
        given().contentType(ContentType.JSON).body(testBody).when().post(path).then().statusCode(201);

        // Nach Deaktivierung des ersten Produktiv-Schemas ist ein neues Produktiv-Schema erlaubt.
        given().when().delete(path + "/" + erstesId).then().statusCode(200);

        given()
            .contentType(ContentType.JSON)
            .body(zweitesBody)
            .when().post(path)
            .then()
            .statusCode(201);
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void crossTenantZugriffAufSchemaWirdVerweigert() {
        final String schultraegerA = neuenSchultraegerAnlegen();
        final String schultraegerB = neuenSchultraegerAnlegen();
        final String schuleA = neueSchuleAnlegen(schultraegerA, "666666", "Schule Sechs");
        final String instanzId = neueSvwsInstanzAnlegen();

        final String createBody = "{\"instanzId\": \"" + instanzId + "\", \"schemaName\": \"666666\", \"umgebung\": \"PRODUKTIV\"}";
        final String schemaId = given().contentType(ContentType.JSON).body(createBody)
            .when().post(schemataPath(schultraegerA, schuleA)).then().statusCode(201).extract().path("id");

        // Fremder Schulträger kennt die Schule (und damit den Pfad) gar nicht -> 404 statt 200/leer.
        given()
            .when().get(schemataPath(schultraegerB, schuleA))
            .then()
            .statusCode(404)
            .contentType("application/problem+json");

        given()
            .when().get(schemataPath(schultraegerA, schuleA))
            .then()
            .statusCode(200)
            .body("size()", equalTo(1))
            .body("[0].id", equalTo(schemaId));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void destroyGeplantesSchemaErgibt409UndBleibtErhalten() {
        final String schultraegerId = neuenSchultraegerAnlegen();
        final String schuleId = neueSchuleAnlegen(schultraegerId, "777771", "Schule Sieben A");
        final String instanzId = neueSvwsInstanzAnlegen();
        final String path = schemataPath(schultraegerId, schuleId);

        final String createBody = "{\"instanzId\": \"" + instanzId + "\", \"schemaName\": \"777771\", \"umgebung\": \"TEST\"}";
        final String id = given().contentType(ContentType.JSON).body(createBody).when().post(path)
            .then().statusCode(201).extract().path("id");

        given()
            .when().post(path + "/" + id + "/loeschen-auf-instanz")
            .then()
            .statusCode(409)
            .contentType("application/problem+json");

        given().when().get(path).then().statusCode(200).body("size()", equalTo(1));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void destroyOhneZugangsdatenLiefertKontrolliertenFehlschlagUndAuditSuccess() {
        final String schultraegerId = neuenSchultraegerAnlegen();
        final String schuleId = neueSchuleAnlegen(schultraegerId, "777772", "Schule Sieben B");
        final String instanzId = neueSvwsInstanzAnlegen();
        final String path = schemataPath(schultraegerId, schuleId);
        final String id = echtesSchemaAnlegen(schultraegerId, schuleId, instanzId, "777772");

        given()
            .when().post(path + "/" + id + "/loeschen-auf-instanz")
            .then()
            .statusCode(200)
            .body("success", equalTo(false))
            .body("message", org.hamcrest.Matchers.containsString("Zugangsdaten"));

        given().when().get(path).then().statusCode(200).body("size()", equalTo(1));
        assertThat(auditOutcomesFor(UUID.fromString(id), "SCHEMA_DESTROY")).containsExactly("SUCCESS");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void destroyMitErfolgreichemApiAufrufEntferntLokaleZeile() {
        final String schultraegerId = neuenSchultraegerAnlegen();
        final String schuleId = neueSchuleAnlegen(schultraegerId, "777773", "Schule Sieben C");
        final String instanzId = neueSvwsInstanzAnlegen();
        zugangsdatenSetzen(instanzId);
        final String path = schemataPath(schultraegerId, schuleId);
        final String id = echtesSchemaAnlegen(schultraegerId, schuleId, instanzId, "777773");

        when(privilegedApiClient.destroySchema(any(), any(), any(), any())).thenReturn(SvwsSchemaDestroyResult.erfolgreich());

        given()
            .when().post(path + "/" + id + "/loeschen-auf-instanz")
            .then()
            .statusCode(200)
            .body("success", equalTo(true));

        given().when().get(path).then().statusCode(200).body("size()", equalTo(0));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void destroyMitFehlschlagDerPrivilegedApiBelaesstLokaleZeile() {
        final String schultraegerId = neuenSchultraegerAnlegen();
        final String schuleId = neueSchuleAnlegen(schultraegerId, "777774", "Schule Sieben D");
        final String instanzId = neueSvwsInstanzAnlegen();
        zugangsdatenSetzen(instanzId);
        final String path = schemataPath(schultraegerId, schuleId);
        final String id = echtesSchemaAnlegen(schultraegerId, schuleId, instanzId, "777774");

        when(privilegedApiClient.destroySchema(any(), any(), any(), any()))
            .thenReturn(SvwsSchemaDestroyResult.failure("Das Schema darf nicht gelöscht werden."));

        given()
            .when().post(path + "/" + id + "/loeschen-auf-instanz")
            .then()
            .statusCode(200)
            .body("success", equalTo(false))
            .body("message", org.hamcrest.Matchers.containsString("darf nicht gelöscht werden"));

        given().when().get(path).then().statusCode(200).body("size()", equalTo(1));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void destroyUnbekanntesSchemaErgibt404() {
        final String schultraegerId = neuenSchultraegerAnlegen();
        final String schuleId = neueSchuleAnlegen(schultraegerId, "777775", "Schule Sieben E");

        given()
            .when().post(schemataPath(schultraegerId, schuleId) + "/" + UUID.randomUUID() + "/loeschen-auf-instanz")
            .then()
            .statusCode(404)
            .contentType("application/problem+json");
    }

    /** Legt ein Schema an und hebt es per Update auf einen "echten" Status (ungleich GEPLANT). */
    static String echtesSchemaAnlegen(final String schultraegerId, final String schuleId, final String instanzId,
            final String schemaName) {
        final String path = schemataPath(schultraegerId, schuleId);
        final String createBody = "{\"instanzId\": \"" + instanzId + "\", \"schemaName\": \"" + schemaName + "\", "
            + "\"umgebung\": \"TEST\"}";
        final String id = given().contentType(ContentType.JSON).body(createBody).when().post(path)
            .then().statusCode(201).extract().path("id");

        final String updateBody = "{\"instanzId\": \"" + instanzId + "\", \"schemaName\": \"" + schemaName + "\", "
            + "\"umgebung\": \"TEST\", \"status\": \"AKTIV\", \"aktiv\": true}";
        given().contentType(ContentType.JSON).body(updateBody).when().put(path + "/" + id).then().statusCode(200);
        return id;
    }

    static void zugangsdatenSetzen(final String instanzId) {
        final String body = "{\"username\": \"svws-admin\", \"password\": \"geheim\"}";
        given().contentType(ContentType.JSON).body(body).when().put(SVWS_INSTANZ_PATH + "/" + instanzId + "/credentials")
            .then().statusCode(204);
    }

    static String schemataPath(final String schultraegerId, final String schuleId) {
        return SCHULTRAEGER_PATH + "/" + schultraegerId + "/schulen/" + schuleId + "/schemata";
    }

    static String neuenSchultraegerAnlegen() {
        final String traegernummer = "SCH-" + UUID.randomUUID();
        final String body = "{\"name\": \"Testträger\", \"traegernummer\": \"" + traegernummer + "\"}";
        return given().contentType(ContentType.JSON).body(body).when().post(SCHULTRAEGER_PATH)
            .then().statusCode(201).extract().path("id");
    }

    static String neueSchuleAnlegen(final String schultraegerId, final String schulnummer, final String name) {
        final String body = "{\"schulnummer\": \"" + schulnummer + "\", \"name\": \"" + name + "\"}";
        return given().contentType(ContentType.JSON).body(body)
            .when().post(SCHULTRAEGER_PATH + "/" + schultraegerId + "/schulen")
            .then().statusCode(201).extract().path("id");
    }

    static String neueSvwsInstanzAnlegen() {
        final String baseUrl = "https://svws-schema-test-" + UUID.randomUUID() + ".example.org";
        final String body = "{\"name\": \"Instanz-" + UUID.randomUUID() + "\", \"baseUrl\": \"" + baseUrl + "\"}";
        return given().contentType(ContentType.JSON).body(body).when().post(SVWS_INSTANZ_PATH)
            .then().statusCode(201).extract().path("id");
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
