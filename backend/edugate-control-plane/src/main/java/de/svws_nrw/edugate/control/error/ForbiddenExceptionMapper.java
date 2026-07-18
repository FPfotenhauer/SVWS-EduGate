package de.svws_nrw.edugate.control.error;

import io.quarkus.security.ForbiddenException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Authentifiziert, aber ohne die erforderliche Rolle ({@code dienstleister-admin}) → 403 Problem+JSON. */
@Provider
public class ForbiddenExceptionMapper implements ExceptionMapper<ForbiddenException> {

    @Override
    public Response toResponse(final ForbiddenException exception) {
        final ProblemDetail problem = ProblemDetail.of(
            "urn:problem-type:forbidden",
            "Zugriff verweigert",
            403,
            "Für diese Anfrage fehlt die erforderliche Rolle.");

        return Response.status(403)
            .type(MediaType.valueOf(ProblemMediaType.APPLICATION_PROBLEM_JSON))
            .entity(problem)
            .build();
    }
}
