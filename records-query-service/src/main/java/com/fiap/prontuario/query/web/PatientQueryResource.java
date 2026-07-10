package com.fiap.prontuario.query.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.prontuario.query.access.PatientByUnit;
import com.fiap.prontuario.query.access.PatientQueryService;
import com.fiap.prontuario.query.access.PatientSummary;
import com.fiap.prontuario.query.access.TimelineEntry;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

/**
 * Endpoints de consulta do prontuario (issue #9): implementam a query
 * ViewPatientRecord do event storming (prontuario consolidado e resumo,
 * protegidos pelo mesmo controle de acesso do agregado) e a busca de
 * pacientes por unidade (sem controle de acesso por paciente, apenas
 * autenticacao).
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class PatientQueryResource {

    private final PatientQueryService queryService;
    private final JsonWebToken jwt;
    private final ObjectMapper objectMapper;

    @Inject
    public PatientQueryResource(PatientQueryService queryService, JsonWebToken jwt, ObjectMapper objectMapper) {
        this.queryService = queryService;
        this.jwt = jwt;
        this.objectMapper = objectMapper;
    }

    @GET
    @Path("/patients/{patientId}/record")
    public List<TimelineEntryResponse> getConsolidatedRecord(
            @PathParam("patientId") String patientId, @QueryParam("unitId") String unitId) {
        List<TimelineEntry> timeline = queryService.getConsolidatedRecord(patientId, currentProfessionalId(), unitId);
        return timeline.stream().map(this::toResponse).toList();
    }

    @GET
    @Path("/patients/{patientId}/summary")
    public PatientSummaryResponse getSummary(
            @PathParam("patientId") String patientId, @QueryParam("unitId") String unitId) {
        PatientSummary summary = queryService.getSummary(patientId, currentProfessionalId(), unitId);
        return toResponse(summary);
    }

    @GET
    @Path("/units/{unitId}/patients")
    public List<PatientByUnitResponse> listPatientsByUnit(@PathParam("unitId") String unitId) {
        return queryService.listPatientsByUnit(unitId).stream().map(this::toResponse).toList();
    }

    private String currentProfessionalId() {
        return jwt.getName();
    }

    private TimelineEntryResponse toResponse(TimelineEntry entry) {
        return new TimelineEntryResponse(entry.eventType(), entry.occurredAt(), entry.professionalId(), entry.unitId(),
                entry.payload());
    }

    private PatientSummaryResponse toResponse(PatientSummary summary) {
        try {
            return new PatientSummaryResponse(
                    summary.patientId(),
                    summary.fullName(),
                    summary.cpf(),
                    summary.unitId(),
                    objectMapper.readTree(summary.allergiesJson()),
                    objectMapper.readTree(summary.activeDiagnosesJson()),
                    objectMapper.readTree(summary.lastPrescriptionsJson()),
                    summary.updatedAt());
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao converter resumo_paciente para resposta", e);
        }
    }

    private PatientByUnitResponse toResponse(PatientByUnit patient) {
        return new PatientByUnitResponse(patient.patientId(), patient.fullName());
    }
}
