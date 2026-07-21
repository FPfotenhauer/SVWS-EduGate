package de.svws_nrw.edugate.control.schuldatei;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.Mockito.when;

import de.svws_nrw.edugate.control.support.PostgresTestResource;
import de.svws_nrw.edugate.core.schuldatei.NrwSchuldateiClient;
import de.svws_nrw.edugate.core.schuldatei.NrwSchuldateiResult;
import de.svws_nrw.edugate.core.schuldatei.NrwSchuleEintrag;
import de.svws_nrw.edugate.core.schuldatei.NrwSchultraegerEintrag;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Landes-Schuldatei als Referenzkatalog (ADR-020-Nachtrag): Refresh (Erfolg/Fehlschlag, stabile
 * Identität über mehrere Refreshs hinweg), Such-/Filterlisten für Schulen/Schulträger, Status und
 * Audit.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class SchuldateiResourceTest {

    private static final String PATH = "/admin/api/v1/schuldatei";
    private static final String ADMIN_USER = "admin@edugate.local";

    @InjectMock
    NrwSchuldateiClient nrwSchuldateiClient;

    @Test
    void ohneTokenGibtEs401AlsProblemJson() {
        given().when().get(PATH + "/schulen").then().statusCode(401).contentType("application/problem+json");
        given().when().get(PATH + "/schultraeger").then().statusCode(401).contentType("application/problem+json");
        given().when().get(PATH + "/status").then().statusCode(401).contentType("application/problem+json");
        given().when().post(PATH + "/refresh").then().statusCode(401).contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = "user-ohne-rolle", roles = {})
    void mitTokenOhneRolleGibtEs403AlsProblemJson() {
        given().when().get(PATH + "/schulen").then().statusCode(403).contentType("application/problem+json");
        given().when().post(PATH + "/refresh").then().statusCode(403).contentType("application/problem+json");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void erfolgreicherRefreshAktualisiertKatalogeUndStatus() {
        final String marker = "m" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        final String schulnummer = marker + "01";
        final String traegernummer = marker + "99";

        when(nrwSchuldateiClient.fetchSchuldatei()).thenReturn(NrwSchuldateiResult.success(
            List.of(new NrwSchuleEintrag(schulnummer, "Schule " + marker, traegernummer, "25", "Musterweg 1",
                "44799", "Bochum", "05911", "0234-1", "0234-2", "info@" + marker + ".de", "www." + marker + ".de",
                "31.12.9999")),
            List.of(new NrwSchultraegerEintrag(traegernummer, "Träger " + marker, "kreisfreie Stadt",
                "Trägerstr. 1", "44777", "Bochum", "31.12.9999"))));

        given().when().post(PATH + "/refresh")
            .then()
            .statusCode(200)
            .body("erfolgreich", equalTo(true))
            .body("anzahlSchulen", equalTo(1))
            .body("anzahlSchultraeger", equalTo(1))
            .body("ausgeloestVon", equalTo(ADMIN_USER));

        given().when().get(PATH + "/status")
            .then()
            .statusCode(200)
            .body("erfolgreich", equalTo(true))
            .body("anzahlSchulen", equalTo(1));

        given().queryParam("q", marker).when().get(PATH + "/schulen")
            .then()
            .statusCode(200)
            .body("items.size()", equalTo(1))
            .body("items[0].schulnummer", equalTo(schulnummer))
            .body("items[0].schultraegernummer", equalTo(traegernummer))
            .body("items[0].schultraegername", equalTo("Träger " + marker))
            .body("items[0].aktiv", equalTo(true));

        given().queryParam("q", marker).when().get(PATH + "/schultraeger")
            .then()
            .statusCode(200)
            .body("items.size()", equalTo(1))
            .body("items[0].traegernummer", equalTo(traegernummer))
            .body("items[0].traegerschaftsart", equalTo("kreisfreie Stadt"));

        assertThat(auditOutcomesForAction("SCHULDATEI_IMPORT")).contains("SUCCESS");
        assertThat(auditOutcomesForAction("SCHULDATEI_SCHULEN_LIST")).contains("SUCCESS");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void refreshMitFehlschlagLiefertKontrolliertenStatusUndAuditSuccess() {
        when(nrwSchuldateiClient.fetchSchuldatei())
            .thenReturn(NrwSchuldateiResult.failure("Zeitüberschreitung beim Abruf der NRW-Schuldatei."));

        given().when().post(PATH + "/refresh")
            .then()
            .statusCode(200)
            .body("erfolgreich", equalTo(false))
            .body("fehlermeldung", equalTo("Zeitüberschreitung beim Abruf der NRW-Schuldatei."));

        assertThat(auditOutcomesForAction("SCHULDATEI_IMPORT")).contains("SUCCESS");
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void zweiterRefreshAktualisiertBestehendenEintragPerUpsertStattNeuanlage() {
        final String marker = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        final String schulnummer = marker + "01";

        when(nrwSchuldateiClient.fetchSchuldatei()).thenReturn(NrwSchuldateiResult.success(
            List.of(new NrwSchuleEintrag(schulnummer, "Alter Name", null, null, null, null, null, null, null, null,
                null, null, "31.12.9999")),
            List.of()));
        given().when().post(PATH + "/refresh").then().statusCode(200);

        final String ersteId = given().queryParam("q", marker).when().get(PATH + "/schulen")
            .then().statusCode(200).extract().path("items[0].id");

        when(nrwSchuldateiClient.fetchSchuldatei()).thenReturn(NrwSchuldateiResult.success(
            List.of(new NrwSchuleEintrag(schulnummer, "Neuer Name", null, null, null, null, null, null, null, null,
                null, null, "31.12.9999")),
            List.of()));
        given().when().post(PATH + "/refresh").then().statusCode(200);

        given().queryParam("q", marker).when().get(PATH + "/schulen")
            .then()
            .statusCode(200)
            .body("items.size()", equalTo(1))
            .body("items[0].id", equalTo(ersteId))
            .body("items[0].schulname", equalTo("Neuer Name"));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void filterNurAktiveUnterscheidetAufgelosteVonAktivenSchulen() {
        final String marker = "a" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        final String aktiveSchule = marker + "01";
        final String aufgelosteSchule = marker + "02";

        when(nrwSchuldateiClient.fetchSchuldatei()).thenReturn(NrwSchuldateiResult.success(
            List.of(
                new NrwSchuleEintrag(aktiveSchule, "Aktive Schule " + marker, null, null, null, null, null, null,
                    null, null, null, null, "31.12.9999"),
                new NrwSchuleEintrag(aufgelosteSchule, "Aufgelöste Schule " + marker, null, null, null, null, null,
                    null, null, null, null, null, "01.01.2000")),
            List.of()));
        given().when().post(PATH + "/refresh").then().statusCode(200);

        given().queryParam("q", marker).queryParam("nurAktive", true).when().get(PATH + "/schulen")
            .then()
            .statusCode(200)
            .body("items.size()", equalTo(1))
            .body("items[0].schulnummer", equalTo(aktiveSchule));

        given().queryParam("q", marker).queryParam("nurAktive", false).when().get(PATH + "/schulen")
            .then()
            .statusCode(200)
            .body("items.size()", equalTo(1))
            .body("items[0].schulnummer", equalTo(aufgelosteSchule));

        given().queryParam("q", marker).when().get(PATH + "/schulen")
            .then().statusCode(200).body("items.size()", equalTo(2));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void filterNachSchultraegernummerZeigtNurDerenSchulen() {
        final String marker = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        final String traegerA = marker + "a";
        final String traegerB = marker + "b";

        when(nrwSchuldateiClient.fetchSchuldatei()).thenReturn(NrwSchuldateiResult.success(
            List.of(
                new NrwSchuleEintrag(marker + "1", "Schule bei A", traegerA, null, null, null, null, null, null,
                    null, null, null, "31.12.9999"),
                new NrwSchuleEintrag(marker + "2", "Schule bei B", traegerB, null, null, null, null, null, null,
                    null, null, null, "31.12.9999")),
            List.of()));
        given().when().post(PATH + "/refresh").then().statusCode(200);

        given().queryParam("q", marker).queryParam("schultraegernummer", traegerA).when().get(PATH + "/schulen")
            .then()
            .statusCode(200)
            .body("items.size()", equalTo(1))
            .body("items[0].schultraegernummer", equalTo(traegerA));
    }

    @Test
    @TestSecurity(user = ADMIN_USER, roles = "dienstleister-admin")
    void paginierungLimitiertUndZaehltGesamtanzahl() {
        given()
            .queryParam("size", 1)
            .when().get(PATH + "/schulen")
            .then()
            .statusCode(200)
            .body("items.size()", org.hamcrest.Matchers.lessThanOrEqualTo(1))
            .body("totalElements", greaterThanOrEqualTo(0));
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
}
