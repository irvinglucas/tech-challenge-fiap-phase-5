package com.fiap.prontuario.query;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Endpoint de verificacao manual do scaffold (issue #2). As consultas reais
 * do prontuario (prontuario consolidado, resumo, por unidade) sao
 * adicionadas na issue #9.
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
