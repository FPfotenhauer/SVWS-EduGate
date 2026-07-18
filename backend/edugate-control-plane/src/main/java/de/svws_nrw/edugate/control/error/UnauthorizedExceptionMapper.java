package de.svws_nrw.edugate.control.error;

import io.quarkus.security.UnauthorizedException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Fehlendes/ungültiges Bearer-Token → 401 Problem+JSON. */
@Provider
public class UnauthorizedExceptionMapper implements ExceptionMapper<UnauthorizedException> {

    @Override
    public Response toResponse(final UnauthorizedException exception) {
        final ProblemDetail problem = ProblemDetail.of(
            "urn:problem-type:unauthorized",
            "Nicht authentifiziert",
            401,
            "Für diese Anfrage ist ein gültiges Bearer-Token erforderlich.");

        return Response.status(401)
            .type(MediaType.valueOf(ProblemMediaType.APPLICATION_PROBLEM_JSON))
            .entity(problem)
            .build();
    }
}
