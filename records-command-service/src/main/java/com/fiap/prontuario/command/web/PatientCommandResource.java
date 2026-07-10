package com.fiap.prontuario.command.web;

import com.fiap.prontuario.command.domain.PatientCommandService;
import com.fiap.prontuario.command.domain.PatientCommandService.CommandResult;

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

/**
 * Endpoints de comando do prontuario (issue #5). Cada endpoint valida a
 * precondicao do comando contra o agregado {@link
 * com.fiap.prontuario.command.domain.PatientRecord} (reconstruido via
 * replay - issue #4) e grava o evento resultante.
 */
@Path("/patients")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class PatientCommandResource {

    private final PatientCommandService commandService;

    @Inject
    public PatientCommandResource(PatientCommandService commandService) {
        this.commandService = commandService;
    }

    @POST
    public Response registerPatient(@Valid RegisterPatientRequest request, @Context UriInfo uriInfo) {
        CommandResult result = commandService.registerPatient(request.fullName(), request.cpf(), request.unitId());
        return Response
                .created(uriInfo.getAbsolutePathBuilder().path(result.patientId()).build())
                .entity(toResponse(result))
                .build();
    }

    @POST
    @Path("/{patientId}/consultations")
    public Response recordConsultation(@PathParam("patientId") String patientId, @Valid ConsultationRequest request) {
        CommandResult result = commandService.recordConsultation(
                patientId, request.professionalId(), request.unitId(), request.notes());
        return Response.status(Response.Status.CREATED).entity(toResponse(result)).build();
    }

    @POST
    @Path("/{patientId}/diagnoses")
    public Response addDiagnosis(@PathParam("patientId") String patientId, @Valid DiagnosisRequest request) {
        CommandResult result = commandService.addDiagnosis(
                patientId, request.professionalId(), request.unitId(), request.description(), request.cid10());
        return Response.status(Response.Status.CREATED).entity(toResponse(result)).build();
    }

    @POST
    @Path("/{patientId}/prescriptions")
    public Response issuePrescription(@PathParam("patientId") String patientId, @Valid PrescriptionRequest request) {
        CommandResult result = commandService.issuePrescription(
                patientId, request.professionalId(), request.unitId(), request.medication(), request.dosage());
        return Response.status(Response.Status.CREATED).entity(toResponse(result)).build();
    }

    @POST
    @Path("/{patientId}/allergies")
    public Response registerAllergy(@PathParam("patientId") String patientId, @Valid AllergyRequest request) {
        CommandResult result = commandService.registerAllergy(
                patientId, request.professionalId(), request.unitId(), request.substance(), request.severity());
        return Response.status(Response.Status.CREATED).entity(toResponse(result)).build();
    }

    @POST
    @Path("/{patientId}/exam-results")
    public Response attachExamResult(@PathParam("patientId") String patientId, @Valid ExamResultRequest request) {
        CommandResult result = commandService.attachExamResult(
                patientId, request.professionalId(), request.unitId(), request.examType(), request.resultSummary());
        return Response.status(Response.Status.CREATED).entity(toResponse(result)).build();
    }

    @POST
    @Path("/{patientId}/access-grants")
    public Response grantAccess(@PathParam("patientId") String patientId, @Valid GrantAccessRequest request) {
        CommandResult result = commandService.grantAccess(
                patientId, request.grantedBy(), request.professionalId(), request.unitId());
        return Response.status(Response.Status.CREATED).entity(toResponse(result)).build();
    }

    @POST
    @Path("/{patientId}/access-revocations")
    public Response revokeAccess(@PathParam("patientId") String patientId, @Valid RevokeAccessRequest request) {
        CommandResult result = commandService.revokeAccess(patientId, request.revokedBy(), request.professionalId());
        return Response.status(Response.Status.CREATED).entity(toResponse(result)).build();
    }

    private PatientCommandResponse toResponse(CommandResult result) {
        return new PatientCommandResponse(result.patientId(), result.version());
    }
}
