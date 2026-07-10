package com.fiap.prontuario.query.web;

import com.fiap.prontuario.common.event.AccessGranted;
import com.fiap.prontuario.common.event.DiagnosisAdded;
import com.fiap.prontuario.common.event.PatientRegistered;
import com.fiap.prontuario.query.projection.ReadModelProjector;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

/**
 * Testa os endpoints de consulta do prontuario (issue #9), incluindo a
 * politica de acesso da query ViewPatientRecord (403 sem acesso concedido,
 * 200 apos AccessGranted) e a busca de pacientes por unidade.
 */
@QuarkusTest
class PatientQueryResourceTest {

    @Inject
    ReadModelProjector projector;

    private static final Instant NOW = Instant.parse("2026-07-09T12:00:00Z");

    private String tokenFor(String professionalId) {
        return Jwt.claims().subject(professionalId).groups("MEDICO").sign();
    }

    @Test
    void rejects_requests_without_a_token() {
        given().when().get("/patients/{patientId}/record?unitId=unit-1", "patient-x")
                .then().statusCode(401);
    }

    @Test
    void unregistered_patient_returns_404() {
        String token = tokenFor("prof-x");
        given().auth().oauth2(token)
                .when().get("/patients/{patientId}/record?unitId=unit-1", "unknown-" + UUID.randomUUID())
                .then().statusCode(404);
    }

    @Test
    void denies_the_record_and_summary_until_access_is_granted_then_allows_it() {
        String patientId = "patient-" + UUID.randomUUID();
        String professionalId = "prof-" + UUID.randomUUID();
        String token = tokenFor(professionalId);

        projector.project(new PatientRegistered(UUID.randomUUID(), patientId, NOW, "Maria Silva", patientId, "unit-1"));
        projector.project(new DiagnosisAdded(UUID.randomUUID(), patientId, NOW, "other-prof", "unit-1", "Hipertensao", "I10"));

        given().auth().oauth2(token)
                .when().get("/patients/{patientId}/record?unitId=unit-2", patientId)
                .then().statusCode(403);

        given().auth().oauth2(token)
                .when().get("/patients/{patientId}/summary?unitId=unit-2", patientId)
                .then().statusCode(403);

        projector.project(new AccessGranted(UUID.randomUUID(), patientId, NOW, "gestor-1", professionalId, "unit-2"));

        given().auth().oauth2(token)
                .when().get("/patients/{patientId}/record?unitId=unit-2", patientId)
                .then().statusCode(200)
                .body("$", hasSize(2))
                .body("[0].eventType", equalTo("PatientRegistered"))
                .body("[1].eventType", equalTo("DiagnosisAdded"));

        given().auth().oauth2(token)
                .when().get("/patients/{patientId}/summary?unitId=unit-2", patientId)
                .then().statusCode(200)
                .body("patientId", equalTo(patientId))
                .body("fullName", equalTo("Maria Silva"))
                .body("activeDiagnoses[0].description", equalTo("Hipertensao"));
    }

    @Test
    void lists_patients_registered_or_granted_to_a_unit() {
        String patientId = "patient-" + UUID.randomUUID();
        String unitId = "unit-" + UUID.randomUUID();
        projector.project(new PatientRegistered(UUID.randomUUID(), patientId, NOW, "Joao Souza", patientId, unitId));

        given().auth().oauth2(tokenFor("prof-y")).contentType(ContentType.JSON)
                .when().get("/units/{unitId}/patients", unitId)
                .then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].patientId", equalTo(patientId))
                .body("[0].fullName", equalTo("Joao Souza"));
    }
}
