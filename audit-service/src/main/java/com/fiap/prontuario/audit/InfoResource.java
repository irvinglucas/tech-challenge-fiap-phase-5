package com.fiap.prontuario.audit;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Endpoint de verificacao manual do scaffold (issue #2). O log de auditoria
 * e a deteccao de acesso anomalo sao adicionados nas issues #10 e #11.
 */
@Path("/info")
public class InfoResource {

    @ConfigProperty(name = "quarkus.application.name")
    String applicationName;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public InfoResponse info() {
        return new InfoResponse(applicationName, "UP");
    }

    public record InfoResponse(String service, String status) {
    }
}
