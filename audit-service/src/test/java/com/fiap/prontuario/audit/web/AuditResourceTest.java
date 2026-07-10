package com.fiap.prontuario.audit.web;

import com.fiap.prontuario.audit.log.AuditLogWriter;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

/**
 * Testa os endpoints de auditoria (issue #11): apenas o papel AUDITOR pode
 * consultar a trilha completa, "quem acessou" e os alertas de acesso
 * anomalo.
 */
@QuarkusTest
class AuditResourceTest {

    @Inject
    AuditLogWriter auditLogWriter;

    private String tokenWithRole(String role) {
        return Jwt.claims().subject("user-" + UUID.randomUUID()).groups(role).sign();
    }

    @Test
    void rejects_requests_without_a_token() {
        given().when().get("/patients/{patientId}/audit-log", "patient-x")
                .then().statusCode(401);
    }

    @Test
    void rejects_roles_other_than_auditor() {
        given().auth().oauth2(tokenWithRole("MEDICO"))
                .when().get("/patients/{patientId}/audit-log", "patient-x")
                .then().statusCode(403);
    }

    @Test
    void returns_the_full_audit_trail_for_a_patient() {
        String patientId = "patient-" + UUID.randomUUID();
        Instant now = Instant.now();
        auditLogWriter.append(patientId, "PatientRegistered", now, null, "unit-1", "Paciente registrado", "corr-1");
        auditLogWriter.append(patientId, "RecordAccessed", now.plusSeconds(1), "prof-1", "unit-1", "Prontuario consultado", "corr-2");
        auditLogWriter.append(patientId, "AccessDenied", now.plusSeconds(2), "prof-2", "unit-2", "Acesso negado: sem permissao", "corr-3");

        given().auth().oauth2(tokenWithRole("AUDITOR"))
                .when().get("/patients/{patientId}/audit-log", patientId)
                .then().statusCode(200)
                .body("$", hasSize(3))
                .body("[0].eventType", equalTo("AccessDenied"));

        given().auth().oauth2(tokenWithRole("AUDITOR"))
                .when().get("/patients/{patientId}/access-log", patientId)
                .then().statusCode(200)
                .body("$", hasSize(2))
                .body("[0].eventType", equalTo("AccessDenied"))
                .body("[1].eventType", equalTo("RecordAccessed"));
    }

    @Test
    void lists_suspicious_access_alerts() {
        given().auth().oauth2(tokenWithRole("AUDITOR"))
                .when().get("/alerts/suspicious-access")
                .then().statusCode(200);
    }
}
