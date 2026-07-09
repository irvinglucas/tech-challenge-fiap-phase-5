package com.fiap.prontuario.command;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Endpoint de verificacao manual do scaffold (issue #2). Os comandos reais
 * do prontuario (RegisterPatient, RecordConsultation, ...) sao adicionados
 * na issue #5.
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
