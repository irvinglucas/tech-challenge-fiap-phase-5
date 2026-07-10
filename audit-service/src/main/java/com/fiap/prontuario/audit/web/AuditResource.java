package com.fiap.prontuario.audit.web;

import com.fiap.prontuario.audit.log.AuditLogWriter;
import com.fiap.prontuario.common.security.Roles;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * Endpoints de auditoria (issue #11): trilha completa de um paciente, "quem
 * acessou o prontuario do paciente X" e a lista de alertas de acesso
 * anomalo. Restrito ao papel AUDITOR (ver docs/event-storming.md).
 */
@Path("/patients/{patientId}")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed(Roles.AUDITOR)
public class AuditResource {

    private final AuditLogWriter auditLogWriter;

    @Inject
    public AuditResource(AuditLogWriter auditLogWriter) {
        this.auditLogWriter = auditLogWriter;
    }

    /** Trilha completa (acessos e alteracoes) de um paciente. */
    @GET
    @Path("/audit-log")
    public List<AuditLogEntryResponse> auditLog(@PathParam("patientId") String patientId) {
        return auditLogWriter.findByPatientId(patientId).stream().map(AuditLogEntryResponse::from).toList();
    }

    /** "Quem acessou o prontuario do paciente X": apenas RecordAccessed/AccessDenied. */
    @GET
    @Path("/access-log")
    public List<AuditLogEntryResponse> accessLog(@PathParam("patientId") String patientId) {
        return auditLogWriter.findAccessEventsByPatientId(patientId).stream().map(AuditLogEntryResponse::from).toList();
    }
}
