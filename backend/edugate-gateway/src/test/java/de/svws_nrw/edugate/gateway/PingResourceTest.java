package de.svws_nrw.edugate.gateway;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

@QuarkusTest
class PingResourceTest {

    private static final String PATH = "/gateway/api/v1/ping";

    @Test
    void ohneTokenGibtEs401() {
        given()
            .when().get(PATH)
            .then()
            .statusCode(401);
    }

    @Test
    @TestSecurity(user = "demo-client")
    void mitGueltigemTokenLiefertStatusOk() {
        given()
            .when().get(PATH)
            .then()
            .statusCode(200)
            .body("status", equalTo("ok"));
    }
}
