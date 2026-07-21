package de.svws_nrw.edugate.control.svwsinstanz;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.svws_nrw.edugate.control.support.PostgresTestResource;
import de.svws_nrw.edugate.core.svws.SvwsPrivilegedApiClient;
import de.svws_nrw.edugate.core.svws.SvwsSchemaListResult;
import de.svws_nrw.edugate.core.svws.SvwsSchemaListeEintrag;
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
 * Read-only Sync mit der SVWS-Privileged-API (ADR-014 Stufe 1): Sync-Ergebnis, gespeicherte
 * Funde, Zuordnungsklassifikation gegenüber EduGate-Schemata (bekannt/unzugeordnet/konflikt,
 * ADR-013) und Audit.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SvwsSchemaFundResourceTest {

    private static final String SVWS_INSTANZ_PATH = "/admin/api/v1/svws-instanzen";
    private static final String SCHULTRAEGER_PATH = "/admin/api/v1/schultraeger";
    private static final String ADMIN_USER = "admin@edugate.local";

    @InjectMock
    SvwsPrivilegedApiClient privilegedApiClient;

    @Test
    void ohneTokenGibtEs401AlsProblemJson() {
        given().when().post(SVWS_INSTANZ_PATH + "/" + UUID.randomUUID() + "/schema-sync")
            .then().statusCode(401).contentType("application/problem+json");
        given().when().get(SVWS_INSTANZ_PATH + "/" + UUID.randomUUID() + "/schema-funde")
            .then().statusCode(401).contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = "user-ohne-rolle", roles = {})
    void mitTokenOhneRolleGibtEs403AlsProblemJson() {
        given().when().post(SVWS_INSTANZ_PATH + "/" + UUID.randomUUID() + "/schema-sync")
            .then().statusCode(403).contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void syncFuerUnbekannteInstanzErgibt404() {
        given().when().post(SVWS_INSTANZ_PATH + "/" + UUID.randomUUID() + "/schema-sync")
            .then().statusCode(404).contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void schemaFundeFuerUnbekannteInstanzErgibt404() {
        given().when().get(SVWS_INSTANZ_PATH + "/" + UUID.randomUUID() + "/schema-funde")
            .then().statusCode(404).contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void syncOhneZugangsdatenLiefertKontrolliertenFehlschlagUndAuditSuccess() {
        final String instanzId = neueSvwsInstanzAnlegen();

        given().when().post(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-sync")
            .then()
            .statusCode(200)
            .body("success", equalTo(false))
            .body("gefundeneSchemata", equalTo(0))
            .body("message", org.hamcrest.Matchers.containsString("Zugangsdaten"));

        assertThat(auditOutcomesFor(UUID.fromString(instanzId), "SVWS_INSTANZ_SCHEMA_SYNC")).containsExactly("SUCCESS");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void syncMitFehlschlagDerPrivilegedApiLiefertSicherenFehlschlag() {
        when(privilegedApiClient.listSchemas(any(), any(), any()))
            .thenReturn(SvwsSchemaListResult.failure("Zugangsdaten ungültig oder ohne privilegierten Zugriff."));
        final String instanzId = instanzMitZugangsdatenAnlegen();

        given().when().post(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-sync")
            .then()
            .statusCode(200)
            .body("success", equalTo(false))
            .body("gefundeneSchemata", equalTo(0));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void erfolgreicherSyncSpeichertFundeAlsUnzugeordnet() {
        final String marker = "fund-" + UUID.randomUUID();
        when(privilegedApiClient.listSchemas(any(), any(), any())).thenReturn(SvwsSchemaListResult.success(List.of(
            new SvwsSchemaListeEintrag(marker, marker, true, 3L, false, true, false))));
        final String instanzId = instanzMitZugangsdatenAnlegen();

        given().when().post(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-sync")
            .then()
            .statusCode(200)
            .body("success", equalTo(true))
            .body("gefundeneSchemata", equalTo(1));

        given().when().get(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-funde")
            .then()
            .statusCode(200)
            .body("size()", equalTo(1))
            .body("[0].schemaName", equalTo(marker))
            .body("[0].username", equalTo(marker))
            .body("[0].revision", equalTo(3))
            .body("[0].isSvws", equalTo(true))
            .body("[0].isInConfig", equalTo(true))
            .body("[0].isDeactivated", equalTo(false))
            .body("[0].zuordnungsStatus", equalTo("UNZUGEORDNET"))
            .body("[0].schemaId", org.hamcrest.Matchers.nullValue());

        assertThat(auditOutcomesFor(UUID.fromString(instanzId), "SVWS_INSTANZ_SCHEMA_FUNDE_LIST")).contains("SUCCESS");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void zweiterSyncEntferntNichtMehrGelieferteFunde() {
        final String marker = "stale-" + UUID.randomUUID();
        final String bleibt = marker + "-bleibt";
        final String verschwindet = marker + "-verschwindet";
        final String instanzId = instanzMitZugangsdatenAnlegen();

        when(privilegedApiClient.listSchemas(any(), any(), any())).thenReturn(SvwsSchemaListResult.success(List.of(
            new SvwsSchemaListeEintrag(bleibt, bleibt, true, 1L, false, true, false),
            new SvwsSchemaListeEintrag(verschwindet, verschwindet, true, 1L, false, true, false))));
        given().when().post(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-sync").then().statusCode(200);

        given().when().get(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-funde")
            .then().statusCode(200).body("schemaName", containsInAnyOrder(bleibt, verschwindet));

        when(privilegedApiClient.listSchemas(any(), any(), any())).thenReturn(SvwsSchemaListResult.success(List.of(
            new SvwsSchemaListeEintrag(bleibt, bleibt, true, 2L, false, true, false))));
        given().when().post(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-sync").then().statusCode(200);

        given().when().get(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-funde")
            .then()
            .statusCode(200)
            .body("size()", equalTo(1))
            .body("[0].schemaName", equalTo(bleibt))
            .body("[0].revision", equalTo(2));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void fundMitPassendemEduGateSchemaWirdAlsBekanntKlassifiziert() {
        final String instanzId = instanzMitZugangsdatenAnlegen();
        final String schultraegerId = neuenSchultraegerAnlegen();
        final String schemaName = "800900";
        final String schuleId = neueSchuleAnlegen(schultraegerId, schemaName, "Bekannte Schule");
        neuesSchema(schultraegerId, schuleId, instanzId, schemaName, "PRODUKTIV");

        when(privilegedApiClient.listSchemas(any(), any(), any())).thenReturn(SvwsSchemaListResult.success(List.of(
            new SvwsSchemaListeEintrag(schemaName, schemaName, true, 1L, false, true, false))));
        given().when().post(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-sync").then().statusCode(200);

        given().when().get(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-funde")
            .then()
            .statusCode(200)
            .body("[0].zuordnungsStatus", equalTo("BEKANNT"))
            .body("[0].schuleId", equalTo(schuleId))
            .body("[0].schultraegerId", equalTo(schultraegerId));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void fundMitAbweichendemDeaktiviertStatusWirdAlsKonfliktKlassifiziert() {
        final String instanzId = instanzMitZugangsdatenAnlegen();
        final String schultraegerId = neuenSchultraegerAnlegen();
        final String schemaName = "800901";
        final String schuleId = neueSchuleAnlegen(schultraegerId, schemaName, "Konflikt-Schule");
        neuesSchema(schultraegerId, schuleId, instanzId, schemaName, "PRODUKTIV");

        // EduGate zeigt das Schema als aktiv, SVWS meldet isDeactivated=true -> Konflikt.
        when(privilegedApiClient.listSchemas(any(), any(), any())).thenReturn(SvwsSchemaListResult.success(List.of(
            new SvwsSchemaListeEintrag(schemaName, schemaName, true, 1L, false, true, true))));
        given().when().post(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-sync").then().statusCode(200);

        given().when().get(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-funde")
            .then()
            .statusCode(200)
            .body("[0].zuordnungsStatus", equalTo("KONFLIKT"));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void mehrereFundeWerdenAlleGespeichert() {
        final String marker = "multi-" + UUID.randomUUID();
        final String instanzId = instanzMitZugangsdatenAnlegen();
        when(privilegedApiClient.listSchemas(any(), any(), any())).thenReturn(SvwsSchemaListResult.success(List.of(
            new SvwsSchemaListeEintrag(marker + "-a", marker + "-a", true, 1L, false, true, false),
            new SvwsSchemaListeEintrag(marker + "-b", marker + "-b", true, 1L, false, true, false))));

        given().when().post(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-sync")
            .then().statusCode(200).body("gefundeneSchemata", equalTo(2));

        given().when().get(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-funde")
            .then().statusCode(200).body("", hasSize(2));
    }

    private String instanzMitZugangsdatenAnlegen() {
        final String id = neueSvwsInstanzAnlegen();
        given()
            .contentType(ContentType.JSON)
            .body("{\"username\": \"svws-admin\", \"password\": \"geheim\"}")
            .when().put(SVWS_INSTANZ_PATH + "/" + id + "/credentials")
            .then().statusCode(204);
        return id;
    }

    private String neueSvwsInstanzAnlegen() {
        final String baseUrl = "https://svws-fund-test-" + UUID.randomUUID() + ".example.org";
        final String body = "{\"name\": \"Fund-Instanz-" + UUID.randomUUID() + "\", \"baseUrl\": \"" + baseUrl + "\"}";
        return given().contentType(ContentType.JSON).body(body).when().post(SVWS_INSTANZ_PATH)
            .then().statusCode(201).extract().path("id");
    }

    private String neuenSchultraegerAnlegen() {
        final String traegernummer = "FUND-" + UUID.randomUUID();
        final String body = "{\"name\": \"Testträger\", \"traegernummer\": \"" + traegernummer + "\"}";
        return given().contentType(ContentType.JSON).body(body).when().post(SCHULTRAEGER_PATH)
            .then().statusCode(201).extract().path("id");
    }

    private String neueSchuleAnlegen(final String schultraegerId, final String schulnummer, final String name) {
        final String body = "{\"schulnummer\": \"" + schulnummer + "\", \"name\": \"" + name + "\"}";
        return given().contentType(ContentType.JSON).body(body)
            .when().post(SCHULTRAEGER_PATH + "/" + schultraegerId + "/schulen")
            .then().statusCode(201).extract().path("id");
    }

    private void neuesSchema(final String schultraegerId, final String schuleId, final String instanzId,
            final String schemaName, final String umgebung) {
        final String body = "{\"instanzId\": \"" + instanzId + "\", \"schemaName\": \"" + schemaName + "\", "
            + "\"umgebung\": \"" + umgebung + "\"}";
        given().contentType(ContentType.JSON).body(body)
            .when().post(SCHULTRAEGER_PATH + "/" + schultraegerId + "/schulen/" + schuleId + "/schemata")
            .then().statusCode(201);
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
