package com.fiap.prontuario.query;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class InfoResourceTest {

    @Test
    void returns_service_name_and_up_status() {
        given()
                .when().get("/info")
                .then()
                .statusCode(200)
                .body("service", equalTo("records-query-service"))
                .body("status", equalTo("UP"));
    }
}
