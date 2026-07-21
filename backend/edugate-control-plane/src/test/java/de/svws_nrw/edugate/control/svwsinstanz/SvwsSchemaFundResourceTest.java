package de.svws_nrw.edugate.control.svwsinstanz;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import de.svws_nrw.edugate.control.support.PostgresTestResource;
import de.svws_nrw.edugate.control.svwsinstanz.dto.SchemaFundZuordnungRequest;
import de.svws_nrw.edugate.core.svws.SvwsPrivilegedApiClient;
import de.svws_nrw.edugate.core.svws.SvwsSchemaListResult;
import de.svws_nrw.edugate.core.svws.SvwsSchemaListeEintrag;
import de.svws_nrw.edugate.core.svws.SvwsSchulInfo;
import de.svws_nrw.edugate.core.svws.SvwsSchulInfoResult;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    @Inject
    SvwsSchemaFundService service;

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

    // --- Zuordnungs-Workflow (ADR-014 Schritt 2) ---------------------------------------------

    @Test
    @TestSecurity(user = "user-ohne-rolle", roles = {})
    void zuordnungOhneRolleGibtEs403() {
        given().contentType(ContentType.JSON).body("{}")
            .when().post(SVWS_INSTANZ_PATH + "/" + UUID.randomUUID() + "/schema-funde/" + UUID.randomUUID() + "/zuordnung")
            .then().statusCode(403).contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void zuordnungLegtNeuesSchemaAnUndFundWirdAlsBekanntKlassifiziert() {
        final String instanzId = instanzMitZugangsdatenAnlegen();
        final String schemaName = "800910";
        final String fundId = einzelnenFundAnlegen(instanzId, schemaName, false, false);
        final String schultraegerId = neuenSchultraegerAnlegen();
        final String schuleId = neueSchuleAnlegen(schultraegerId, schemaName, "Neue Schule");

        given().contentType(ContentType.JSON)
            .body(zuordnungBody(schultraegerId, schuleId, "PRODUKTIV", "Testzuordnung"))
            .when().post(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-funde/" + fundId + "/zuordnung")
            .then()
            .statusCode(200)
            .body("id", equalTo(fundId))
            .body("zuordnungsStatus", equalTo("BEKANNT"))
            .body("schuleId", equalTo(schuleId))
            .body("schultraegerId", equalTo(schultraegerId))
            .body("schemaId", org.hamcrest.Matchers.notNullValue());

        given().when().get(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-funde")
            .then().statusCode(200).body("[0].zuordnungsStatus", equalTo("BEKANNT"));

        assertThat(auditOutcomesForAction("SVWS_SCHEMA_FUND_ZUORDNEN")).contains("SUCCESS");
        assertThat(gespeichertesSchema(instanzId, schemaName))
            .containsEntry("status", "VORHANDEN")
            .containsEntry("source", "SYNCHRONISIERT")
            .containsEntry("umgebung", "PRODUKTIV");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void zuordnungSetztStatusAusFundFlagsAb() {
        final String instanzId = instanzMitZugangsdatenAnlegen();
        final String schemaName = "800911";
        final String fundId = einzelnenFundAnlegen(instanzId, schemaName, true, false);
        final String schultraegerId = neuenSchultraegerAnlegen();
        final String schuleId = neueSchuleAnlegen(schultraegerId, schemaName, "Deaktivierte Schule");

        given().contentType(ContentType.JSON)
            .body(zuordnungBody(schultraegerId, schuleId, "TEST", null))
            .when().post(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-funde/" + fundId + "/zuordnung")
            .then().statusCode(200);

        assertThat(gespeichertesSchema(instanzId, schemaName))
            .containsEntry("status", "DEAKTIVIERT")
            .containsEntry("aktiv", false);
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void zuordnungMitUnbekanntemSchultraegerErgibt404() {
        final String instanzId = instanzMitZugangsdatenAnlegen();
        final String fundId = einzelnenFundAnlegen(instanzId, "800912", false, false);

        given().contentType(ContentType.JSON)
            .body(zuordnungBody(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "PRODUKTIV", null))
            .when().post(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-funde/" + fundId + "/zuordnung")
            .then().statusCode(404).contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void zuordnungMitSchuleAusAnderemSchultraegerErgibt404() {
        final String instanzId = instanzMitZugangsdatenAnlegen();
        final String fundId = einzelnenFundAnlegen(instanzId, "800913", false, false);
        final String schultraegerA = neuenSchultraegerAnlegen();
        final String schultraegerB = neuenSchultraegerAnlegen();
        final String schuleUnterB = neueSchuleAnlegen(schultraegerB, "800913", "Schule unter B");

        given().contentType(ContentType.JSON)
            .body(zuordnungBody(schultraegerA, schuleUnterB, "PRODUKTIV", null))
            .when().post(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-funde/" + fundId + "/zuordnung")
            .then().statusCode(404).contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void zuordnungFuerUnbekanntenFundErgibt404() {
        final String instanzId = neueSvwsInstanzAnlegen();
        final String schultraegerId = neuenSchultraegerAnlegen();
        final String schuleId = neueSchuleAnlegen(schultraegerId, "800914", "Schule");

        given().contentType(ContentType.JSON)
            .body(zuordnungBody(schultraegerId, schuleId, "PRODUKTIV", null))
            .when().post(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-funde/" + UUID.randomUUID() + "/zuordnung")
            .then().statusCode(404).contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void erneuteZuordnungAnDieselbeSchuleAktualisiertOhneFehler() {
        final String instanzId = instanzMitZugangsdatenAnlegen();
        final String schemaName = "800915";
        final String fundId = einzelnenFundAnlegen(instanzId, schemaName, false, false);
        final String schultraegerId = neuenSchultraegerAnlegen();
        final String schuleId = neueSchuleAnlegen(schultraegerId, schemaName, "Schule");

        given().contentType(ContentType.JSON)
            .body(zuordnungBody(schultraegerId, schuleId, "TEST", "erste Beschreibung"))
            .when().post(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-funde/" + fundId + "/zuordnung")
            .then().statusCode(200);

        given().contentType(ContentType.JSON)
            .body(zuordnungBody(schultraegerId, schuleId, "SCHULUNG", "korrigierte Beschreibung"))
            .when().post(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-funde/" + fundId + "/zuordnung")
            .then().statusCode(200).body("zuordnungsStatus", equalTo("BEKANNT"));

        assertThat(gespeichertesSchema(instanzId, schemaName))
            .containsEntry("umgebung", "SCHULUNG")
            .containsEntry("beschreibung", "korrigierte Beschreibung");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void zuordnungAnAndereSchuleAlsBereitsZugeordnetErgibtKonflikt() {
        final String instanzId = instanzMitZugangsdatenAnlegen();
        final String schemaName = "800916";
        final String fundId = einzelnenFundAnlegen(instanzId, schemaName, false, false);
        final String schultraegerId = neuenSchultraegerAnlegen();
        final String schuleA = neueSchuleAnlegen(schultraegerId, schemaName + "-a", "Schule A");
        final String schuleB = neueSchuleAnlegen(schultraegerId, schemaName + "-b", "Schule B");

        given().contentType(ContentType.JSON)
            .body(zuordnungBody(schultraegerId, schuleA, "PRODUKTIV", null))
            .when().post(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-funde/" + fundId + "/zuordnung")
            .then().statusCode(200);

        given().contentType(ContentType.JSON)
            .body(zuordnungBody(schultraegerId, schuleB, "PRODUKTIV", null))
            .when().post(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-funde/" + fundId + "/zuordnung")
            .then().statusCode(409).contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void zuordnungMitProduktivKonfliktErgibt409() {
        final String instanzId = instanzMitZugangsdatenAnlegen();
        final String schultraegerId = neuenSchultraegerAnlegen();
        final String schuleId = neueSchuleAnlegen(schultraegerId, "800917", "Schule mit Produktiv");
        neuesSchema(schultraegerId, schuleId, instanzId, "800917", "PRODUKTIV");

        final String zweitesSchema = "800917_zweit";
        final String fundId = einzelnenFundAnlegen(instanzId, zweitesSchema, false, false);

        given().contentType(ContentType.JSON)
            .body(zuordnungBody(schultraegerId, schuleId, "PRODUKTIV", null))
            .when().post(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-funde/" + fundId + "/zuordnung")
            .then().statusCode(409).contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void assignBySchemaNameOrdnetFundUeberSchemanamenZu() {
        final String instanzId = instanzMitZugangsdatenAnlegen();
        final String schemaName = "800918";
        einzelnenFundAnlegen(instanzId, schemaName, false, false);
        final String schultraegerId = neuenSchultraegerAnlegen();
        final String schuleId = neueSchuleAnlegen(schultraegerId, schemaName, "Über Schemaname zugeordnet");

        final SchemaFundZuordnungRequest request = new SchemaFundZuordnungRequest(
            UUID.fromString(schultraegerId), UUID.fromString(schuleId), "PRODUKTIV", null);
        final var dto = service.assignBySchemaName(ADMIN_USER, UUID.fromString(instanzId), schemaName, request);

        assertThat(dto.zuordnungsStatus()).isEqualTo(SchemaFundZuordnungsStatus.BEKANNT);
        assertThat(dto.schuleId()).isEqualTo(UUID.fromString(schuleId));
    }

    // --- SchulInfo (ADR-014 Schritt 2, optional) ----------------------------------------------

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void schulInfoOhneZugangsdatenLiefertKontrolliertenFehlschlagOhne500() {
        // Kein Sync über instanzMitZugangsdatenAnlegen(): der Sync selbst würde Zugangsdaten
        // benötigen. Der Fund wird daher direkt eingefügt, um gezielt den "keine Zugangsdaten"-
        // Zweig von schulInfo() zu testen, unabhängig vom Sync-Workflow.
        final String instanzId = neueSvwsInstanzAnlegen();
        final String fundId = fundDirektEinfuegen(instanzId, "800919");

        given().when().get(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-funde/" + fundId + "/schulinfo")
            .then()
            .statusCode(200)
            .body("success", equalTo(false))
            .body("schulInfo", org.hamcrest.Matchers.nullValue());
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void schulInfoMitErfolgLiefertSchulstammdaten() {
        final String instanzId = instanzMitZugangsdatenAnlegen();
        final String fundId = einzelnenFundAnlegen(instanzId, "800920", false, false);
        when(privilegedApiClient.getSchulInfo(any(), any(), any(), any())).thenReturn(SvwsSchulInfoResult.success(
            new SvwsSchulInfo(800920L, "GY", "Städt. Gymnasium", "Musterweg", "1", null, "42287", "Düsseldorf")));

        given().when().get(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-funde/" + fundId + "/schulinfo")
            .then()
            .statusCode(200)
            .body("success", equalTo(true))
            .body("schulInfo.schulnummer", equalTo(800920))
            .body("schulInfo.bezeichnung", equalTo("Städt. Gymnasium"));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void schulInfoMitFehlschlagDerPrivilegedApiBlockiertZuordnungNicht() {
        final String instanzId = instanzMitZugangsdatenAnlegen();
        final String schemaName = "800921";
        final String fundId = einzelnenFundAnlegen(instanzId, schemaName, false, false);
        when(privilegedApiClient.getSchulInfo(any(), any(), any(), any()))
            .thenReturn(SvwsSchulInfoResult.failure("Keine Schul-Informationen im Schema gefunden."));

        given().when().get(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-funde/" + fundId + "/schulinfo")
            .then().statusCode(200).body("success", equalTo(false));

        final String schultraegerId = neuenSchultraegerAnlegen();
        final String schuleId = neueSchuleAnlegen(schultraegerId, schemaName, "Manuell trotz SchulInfo-Fehlschlag");
        given().contentType(ContentType.JSON)
            .body(zuordnungBody(schultraegerId, schuleId, "PRODUKTIV", null))
            .when().post(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-funde/" + fundId + "/zuordnung")
            .then().statusCode(200).body("zuordnungsStatus", equalTo("BEKANNT"));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void schulInfoFuerUnbekanntenFundErgibt404() {
        final String instanzId = neueSvwsInstanzAnlegen();

        given().when().get(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-funde/" + UUID.randomUUID() + "/schulinfo")
            .then().statusCode(404).contentType("application/problem+json");
    }

    private String einzelnenFundAnlegen(final String instanzId, final String schemaName, final boolean isDeactivated,
            final boolean isTainted) {
        when(privilegedApiClient.listSchemas(any(), any(), any())).thenReturn(SvwsSchemaListResult.success(List.of(
            new SvwsSchemaListeEintrag(schemaName, schemaName, true, 5L, isTainted, true, isDeactivated))));
        given().when().post(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-sync").then().statusCode(200);
        return given().when().get(SVWS_INSTANZ_PATH + "/" + instanzId + "/schema-funde")
            .then().statusCode(200).extract().path("find { it.schemaName == '" + schemaName + "' }.id");
    }

    /**
     * Fügt einen Fund direkt per SQL ein, ohne den Sync-Workflow zu durchlaufen (der selbst
     * Zugangsdaten bräuchte) - für Tests, die gezielt den "keine Zugangsdaten hinterlegt"-Zweig
     * von {@code schulInfo()}/{@code sync()} prüfen wollen.
     */
    private String fundDirektEinfuegen(final String instanzId, final String schemaName) {
        try (Connection connection = PostgresTestResource.openAdminConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "INSERT INTO svws_schema_fund (instanz_id, schema_name, username, is_svws, is_in_config, is_deactivated) "
                     + "VALUES (?, ?, ?, true, true, false) RETURNING id")) {
            statement.setObject(1, UUID.fromString(instanzId));
            statement.setString(2, schemaName);
            statement.setString(3, schemaName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getObject("id").toString();
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String zuordnungBody(final String schultraegerId, final String schuleId, final String umgebung,
            final String beschreibung) {
        final String beschreibungJson = beschreibung == null ? "null" : "\"" + beschreibung + "\"";
        return "{\"schultraegerId\": \"" + schultraegerId + "\", \"schuleId\": \"" + schuleId + "\", "
            + "\"umgebung\": \"" + umgebung + "\", \"beschreibung\": " + beschreibungJson + "}";
    }

    private Map<String, Object> gespeichertesSchema(final String instanzId, final String schemaName) {
        try (Connection connection = PostgresTestResource.openAdminConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT status, aktiv, source, umgebung, beschreibung FROM schema WHERE instanz_id = ? AND schema_name = ?")) {
            statement.setObject(1, UUID.fromString(instanzId));
            statement.setString(2, schemaName);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).as("Schema wurde angelegt").isTrue();
                final Map<String, Object> row = new HashMap<>();
                row.put("status", resultSet.getString("status"));
                row.put("aktiv", resultSet.getBoolean("aktiv"));
                row.put("source", resultSet.getString("source"));
                row.put("umgebung", resultSet.getString("umgebung"));
                row.put("beschreibung", resultSet.getString("beschreibung"));
                return row;
            }
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> auditOutcomesForAction(final String action) {
        try (Connection connection = PostgresTestResource.openAdminConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "SELECT outcome FROM audit_admin WHERE action = ?")) {
            statement.setString(1, action);
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
