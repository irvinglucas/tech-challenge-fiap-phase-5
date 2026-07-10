package com.fiap.prontuario.command.web;

import com.fiap.prontuario.command.domain.PatientCommandService;
import com.fiap.prontuario.command.domain.PatientCommandService.CommandResult;
import com.fiap.prontuario.common.security.Roles;

import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Endpoints de comando do prontuario (issue #5), protegidos por JWT/RBAC
 * (issue #7): cada endpoint exige a role adequada ao ator do comando (ver
 * docs/event-storming.md) e usa o {@code sub} do token como identidade do
 * profissional/gestor autenticado - nunca confia em um professionalId
 * enviado no corpo da requisicao.
 */
@Path("/patients")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class PatientCommandResource {

    private final PatientCommandService commandService;
    private final JsonWebToken jwt;

    @Inject
    public PatientCommandResource(PatientCommandService commandService, JsonWebToken jwt) {
        this.commandService = commandService;
        this.jwt = jwt;
    }

    @POST
    @RolesAllowed({Roles.MEDICO, Roles.ENFERMEIRO, Roles.GESTOR})
    public Response registerPatient(@Valid RegisterPatientRequest request, @Context UriInfo uriInfo) {
        CommandResult result = commandService.registerPatient(request.fullName(), request.cpf(), request.unitId());
        return Response
                .created(uriInfo.getAbsolutePathBuilder().path(result.patientId()).build())
                .entity(toResponse(result))
                .build();
    }

    @POST
    @Path("/{patientId}/consultations")
    @RolesAllowed({Roles.MEDICO, Roles.ENFERMEIRO})
    public Response recordConsultation(@PathParam("patientId") String patientId, @Valid ConsultationRequest request) {
        CommandResult result = commandService.recordConsultation(patientId, currentProfessionalId(), request.unitId(), request.notes());
        return Response.status(Response.Status.CREATED).entity(toResponse(result)).build();
    }

    @POST
    @Path("/{patientId}/diagnoses")
    @RolesAllowed(Roles.MEDICO)
    public Response addDiagnosis(@PathParam("patientId") String patientId, @Valid DiagnosisRequest request) {
        CommandResult result = commandService.addDiagnosis(
                patientId, currentProfessionalId(), request.unitId(), request.description(), request.cid10());
        return Response.status(Response.Status.CREATED).entity(toResponse(result)).build();
    }

    @POST
    @Path("/{patientId}/prescriptions")
    @RolesAllowed(Roles.MEDICO)
    public Response issuePrescription(@PathParam("patientId") String patientId, @Valid PrescriptionRequest request) {
        CommandResult result = commandService.issuePrescription(
                patientId, currentProfessionalId(), request.unitId(), request.medication(), request.dosage());
        return Response.status(Response.Status.CREATED).entity(toResponse(result)).build();
    }

    @POST
    @Path("/{patientId}/allergies")
    @RolesAllowed({Roles.MEDICO, Roles.ENFERMEIRO})
    public Response registerAllergy(@PathParam("patientId") String patientId, @Valid AllergyRequest request) {
        CommandResult result = commandService.registerAllergy(
                patientId, currentProfessionalId(), request.unitId(), request.substance(), request.severity());
        return Response.status(Response.Status.CREATED).entity(toResponse(result)).build();
    }

    @POST
    @Path("/{patientId}/exam-results")
    @RolesAllowed({Roles.MEDICO, Roles.ENFERMEIRO})
    public Response attachExamResult(@PathParam("patientId") String patientId, @Valid ExamResultRequest request) {
        CommandResult result = commandService.attachExamResult(
                patientId, currentProfessionalId(), request.unitId(), request.examType(), request.resultSummary());
        return Response.status(Response.Status.CREATED).entity(toResponse(result)).build();
    }

    @POST
    @Path("/{patientId}/access-grants")
    @RolesAllowed(Roles.GESTOR)
    public Response grantAccess(@PathParam("patientId") String patientId, @Valid GrantAccessRequest request) {
        CommandResult result = commandService.grantAccess(
                patientId, currentProfessionalId(), request.professionalId(), request.unitId());
        return Response.status(Response.Status.CREATED).entity(toResponse(result)).build();
    }

    @POST
    @Path("/{patientId}/access-revocations")
    @RolesAllowed(Roles.GESTOR)
    public Response revokeAccess(@PathParam("patientId") String patientId, @Valid RevokeAccessRequest request) {
        CommandResult result = commandService.revokeAccess(patientId, currentProfessionalId(), request.professionalId());
        return Response.status(Response.Status.CREATED).entity(toResponse(result)).build();
    }

    private String currentProfessionalId() {
        return jwt.getName();
    }

    private PatientCommandResponse toResponse(CommandResult result) {
        return new PatientCommandResponse(result.patientId(), result.version());
    }
}
