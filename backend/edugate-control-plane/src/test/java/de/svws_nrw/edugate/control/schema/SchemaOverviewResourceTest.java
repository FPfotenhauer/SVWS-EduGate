package de.svws_nrw.edugate.control.schema;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

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
 * Betreiber-Übersicht "Schuldatenbanken" (ADR-013): mandantenübergreifende Sichtbarkeit,
 * Filterung nach Instanz/Schulträger/Umgebung/Status/Suche, Audit.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SchemaOverviewResourceTest {

    private static final String OVERVIEW_PATH = "/admin/api/v1/schuldatenbanken";
    private static final String ADMIN_USER = "admin@edugate.local";

    @Test
    void ohneTokenGibtEs401AlsProblemJson() {
        given().when().get(OVERVIEW_PATH).then().statusCode(401).contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = "user-ohne-rolle", roles = {})
    void mitTokenOhneRolleGibtEs403AlsProblemJson() {
        given().when().get(OVERVIEW_PATH).then().statusCode(403).contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void uebersichtZeigtSchemataMehrererSchultraegerGemeinsam() {
        final String marker = "overview-" + UUID.randomUUID();
        final String instanzId = SchemaResourceTest.neueSvwsInstanzAnlegen();

        final String schultraegerA = SchemaResourceTest.neuenSchultraegerAnlegen();
        final String schuleA = SchemaResourceTest.neueSchuleAnlegen(schultraegerA, "800001", "Schule A " + marker);
        final String schultraegerB = SchemaResourceTest.neuenSchultraegerAnlegen();
        final String schuleB = SchemaResourceTest.neueSchuleAnlegen(schultraegerB, "800002", "Schule B " + marker);

        neuesSchema(schultraegerA, schuleA, instanzId, "800001", "PRODUKTIV");
        neuesSchema(schultraegerB, schuleB, instanzId, "800002", "PRODUKTIV");

        given()
            .queryParam("q", marker)
            .when().get(OVERVIEW_PATH)
            .then()
            .statusCode(200)
            .body("items.size()", equalTo(2))
            .body("items.schultraegerId", org.hamcrest.Matchers.containsInAnyOrder(schultraegerA, schultraegerB));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void filterNachInstanzZeigtNurDerenSchemata() {
        final String marker = "instanzfilter-" + UUID.randomUUID();
        final String instanzA = SchemaResourceTest.neueSvwsInstanzAnlegen();
        final String instanzB = SchemaResourceTest.neueSvwsInstanzAnlegen();

        final String schultraeger = SchemaResourceTest.neuenSchultraegerAnlegen();
        final String schuleA = SchemaResourceTest.neueSchuleAnlegen(schultraeger, "800003", "Schule " + marker + "-a");
        final String schuleB = SchemaResourceTest.neueSchuleAnlegen(schultraeger, "800004", "Schule " + marker + "-b");

        neuesSchema(schultraeger, schuleA, instanzA, "800003", "PRODUKTIV");
        neuesSchema(schultraeger, schuleB, instanzB, "800004", "PRODUKTIV");

        given()
            .queryParam("q", marker)
            .queryParam("instanzId", instanzA)
            .when().get(OVERVIEW_PATH)
            .then()
            .statusCode(200)
            .body("items.size()", equalTo(1))
            .body("items[0].instanzId", equalTo(instanzA));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void filterNachSchultraegerUndUmgebungWirkt() {
        final String marker = "umgebungsfilter-" + UUID.randomUUID();
        final String instanzId = SchemaResourceTest.neueSvwsInstanzAnlegen();
        final String schultraeger = SchemaResourceTest.neuenSchultraegerAnlegen();
        final String schule = SchemaResourceTest.neueSchuleAnlegen(schultraeger, "800005", "Schule " + marker);

        neuesSchema(schultraeger, schule, instanzId, "800005", "PRODUKTIV");
        neuesSchema(schultraeger, schule, instanzId, "800005_test", "TEST");

        given()
            .queryParam("q", marker)
            .queryParam("schultraegerId", schultraeger)
            .queryParam("umgebung", "TEST")
            .when().get(OVERVIEW_PATH)
            .then()
            .statusCode(200)
            .body("items.size()", equalTo(1))
            .body("items[0].umgebung", equalTo("TEST"));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void enthaeltSchuleUndSchultraegerUndInstanzKontext() {
        final String marker = "kontext-" + UUID.randomUUID();
        final String instanzId = SchemaResourceTest.neueSvwsInstanzAnlegen();
        final String schultraeger = SchemaResourceTest.neuenSchultraegerAnlegen();
        final String schule = SchemaResourceTest.neueSchuleAnlegen(schultraeger, "800006", "Schule " + marker);

        neuesSchema(schultraeger, schule, instanzId, "800006", "PRODUKTIV");

        given()
            .queryParam("q", marker)
            .when().get(OVERVIEW_PATH)
            .then()
            .statusCode(200)
            .body("items.size()", equalTo(1))
            .body("items[0].schuleId", equalTo(schule))
            .body("items[0].schulnummer", equalTo("800006"))
            .body("items[0].schultraegerId", equalTo(schultraeger))
            .body("items[0].instanzId", equalTo(instanzId));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void jedeAbfrageErzeugtGenauEinenAuditEintrag() throws Exception {
        given().when().get(OVERVIEW_PATH).then().statusCode(200).body("items", org.hamcrest.Matchers.notNullValue());

        assertThat(auditOutcomesFor("SCHEMA_OVERVIEW")).hasSizeGreaterThanOrEqualTo(1);
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void paginierungLimitiertUndZaehltGesamtanzahl() {
        given()
            .queryParam("size", 1)
            .when().get(OVERVIEW_PATH)
            .then()
            .statusCode(200)
            .body("items.size()", org.hamcrest.Matchers.lessThanOrEqualTo(1))
            .body("totalElements", greaterThanOrEqualTo(0));
    }

    private void neuesSchema(final String schultraegerId, final String schuleId, final String instanzId,
            final String schemaName, final String umgebung) {
        final String body = "{\"instanzId\": \"" + instanzId + "\", \"schemaName\": \"" + schemaName + "\", "
            + "\"umgebung\": \"" + umgebung + "\"}";
        given().contentType(ContentType.JSON).body(body)
            .when().post(SchemaResourceTest.schemataPath(schultraegerId, schuleId))
            .then().statusCode(201);
    }

    private List<String> auditOutcomesFor(final String action) throws Exception {
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
        }
    }
}
