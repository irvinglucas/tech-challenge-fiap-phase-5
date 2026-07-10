package com.fiap.prontuario.command;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * Verifica os endpoints de observabilidade (issue #12): health checks
 * (liveness/readiness, incluindo os checks automaticos do datasource e do
 * Kafka) e metricas no formato Prometheus.
 */
@QuarkusTest
class ObservabilityEndpointsTest {

    @Test
    void health_reports_up() {
        given().when().get("/q/health")
                .then().statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    void liveness_reports_up() {
        given().when().get("/q/health/live")
                .then().statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    void readiness_reports_up() {
        given().when().get("/q/health/ready")
                .then().statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    void metrics_are_exposed_in_prometheus_format() {
        given().when().get("/q/metrics")
                .then().statusCode(200)
                .body(containsString("jvm_classes_loaded_count_classes"));
    }
}
