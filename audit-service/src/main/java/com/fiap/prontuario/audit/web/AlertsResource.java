package com.fiap.prontuario.audit.web;

import com.fiap.prontuario.audit.anomaly.SuspiciousAccessDetector;
import com.fiap.prontuario.common.security.Roles;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * Consulta de alertas de acesso anomalo (issue #11, evento derivado
 * SuspiciousAccessDetected). Restrito ao papel AUDITOR.
 */
@Path("/alerts/suspicious-access")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed(Roles.AUDITOR)
public class AlertsResource {

    private final SuspiciousAccessDetector detector;

    @Inject
    public AlertsResource(SuspiciousAccessDetector detector) {
        this.detector = detector;
    }

    @GET
    public List<SuspiciousAccessAlertResponse> list() {
        return detector.findAll().stream().map(SuspiciousAccessAlertResponse::from).toList();
    }
}
