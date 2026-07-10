package com.fiap.prontuario.command.web;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Testa o fluxo principal dos comandos do prontuario (issue #5) de ponta a
 * ponta via REST, incluindo as precondicoes de dominio (paciente
 * registrado, profissional autorizado) e o conflito de concorrencia
 * otimista (issue #4) surfacado como 409.
 */
@QuarkusTest
class PatientCommandResourceTest {

    private String newCpf() {
        return String.valueOf(System.nanoTime());
    }

    @Test
    void registers_a_patient_and_returns_201_with_location() {
        String cpf = newCpf();

        given().contentType(ContentType.JSON)
                .body(Map.of("fullName", "Maria Silva", "cpf", cpf, "unitId", "unit-1"))
                .when().post("/patients")
                .then().statusCode(201)
                .header("Location", org.hamcrest.Matchers.containsString(cpf))
                .body("patientId", equalTo(cpf))
                .body("version", equalTo(1));
    }

    @Test
    void rejects_registering_the_same_cpf_twice() {
        String cpf = newCpf();
        registerPatient(cpf);

        given().contentType(ContentType.JSON)
                .body(Map.of("fullName", "Maria Silva", "cpf", cpf, "unitId", "unit-1"))
                .when().post("/patients")
                .then().statusCode(409);
    }

    @Test
    void full_clinical_flow_requires_granted_access() {
        String patientId = newCpf();
        registerPatient(patientId);

        // Sem acesso concedido ainda -> 403.
        given().contentType(ContentType.JSON)
                .body(Map.of("professionalId", "prof-1", "unitId", "unit-2", "notes", "Consulta de rotina"))
                .when().post("/patients/{patientId}/consultations", patientId)
                .then().statusCode(403);

        // Gestor concede acesso ao profissional de outra unidade.
        given().contentType(ContentType.JSON)
                .body(Map.of("grantedBy", "gestor-1", "professionalId", "prof-1", "unitId", "unit-2"))
                .when().post("/patients/{patientId}/access-grants", patientId)
                .then().statusCode(201)
                .body("version", equalTo(2));

        // Agora o mesmo comando e aceito.
        given().contentType(ContentType.JSON)
                .body(Map.of("professionalId", "prof-1", "unitId", "unit-2", "notes", "Consulta de rotina"))
                .when().post("/patients/{patientId}/consultations", patientId)
                .then().statusCode(201)
                .body("version", equalTo(3));

        given().contentType(ContentType.JSON)
                .body(Map.of("professionalId", "prof-1", "unitId", "unit-2", "description", "Hipertensao", "cid10", "I10"))
                .when().post("/patients/{patientId}/diagnoses", patientId)
                .then().statusCode(201)
                .body("version", equalTo(4));

        // Revogado o acesso, o comando volta a ser negado.
        given().contentType(ContentType.JSON)
                .body(Map.of("revokedBy", "gestor-1", "professionalId", "prof-1"))
                .when().post("/patients/{patientId}/access-revocations", patientId)
                .then().statusCode(201)
                .body("version", equalTo(5));

        given().contentType(ContentType.JSON)
                .body(Map.of("professionalId", "prof-1", "unitId", "unit-2", "notes", "Outra consulta"))
                .when().post("/patients/{patientId}/consultations", patientId)
                .then().statusCode(403);
    }

    @Test
    void revoking_access_that_was_never_granted_returns_409() {
        String patientId = newCpf();
        registerPatient(patientId);

        given().contentType(ContentType.JSON)
                .body(Map.of("revokedBy", "gestor-1", "professionalId", "prof-x"))
                .when().post("/patients/{patientId}/access-revocations", patientId)
                .then().statusCode(409);
    }

    @Test
    void commands_for_an_unregistered_patient_return_404() {
        String unknownPatientId = "unknown-" + UUID.randomUUID();

        given().contentType(ContentType.JSON)
                .body(Map.of("professionalId", "prof-1", "unitId", "unit-1", "notes", "Consulta"))
                .when().post("/patients/{patientId}/consultations", unknownPatientId)
                .then().statusCode(404);
    }

    @Test
    void missing_required_fields_return_400() {
        given().contentType(ContentType.JSON)
                .body(Map.of("fullName", "", "cpf", "", "unitId", ""))
                .when().post("/patients")
                .then().statusCode(400);
    }

    private void registerPatient(String cpf) {
        given().contentType(ContentType.JSON)
                .body(Map.of("fullName", "Paciente Teste", "cpf", cpf, "unitId", "unit-1"))
                .when().post("/patients")
                .then().statusCode(201);
    }
}
