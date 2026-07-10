package com.fiap.prontuario.command.security;

import io.smallrye.jwt.build.Jwt;

import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.time.Duration;

/**
 * Emissor de tokens de demonstracao (issue #7): assina um JWT com as roles
 * informadas usando a chave privada de demo (src/main/resources/privateKey.pem),
 * para permitir testar os endpoints de comando via Postman/Swagger sem
 * depender de um Authorization Server externo.
 *
 * <p><b>Apenas para fins de demonstracao/hackathon.</b> Em um cenario real,
 * os tokens seriam emitidos por um Authorization Server (ex.: Keycloak) e
 * este endpoint nao existiria.
 */
@Path("/dev/tokens")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TokenIssuerResource {

    @POST
    public TokenResponse issueToken(@Valid TokenIssuerRequest request) {
        String token = Jwt.claims()
                .subject(request.professionalId())
                .groups(request.roles())
                .expiresIn(Duration.ofHours(8))
                .sign();
        return new TokenResponse(token);
    }

    public record TokenResponse(String token) {
    }
}
