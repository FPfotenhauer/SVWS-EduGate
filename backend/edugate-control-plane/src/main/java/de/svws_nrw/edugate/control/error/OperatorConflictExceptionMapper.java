package de.svws_nrw.edugate.control.error;

import de.svws_nrw.edugate.control.operator.OperatorConflictException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Eindeutigkeitsverletzung (z. B. doppelte Trägernummer) → 409 Problem+JSON. */
@Provider
public class OperatorConflictExceptionMapper implements ExceptionMapper<OperatorConflictException> {

    @Override
    public Response toResponse(final OperatorConflictException exception) {
        final ProblemDetail problem = ProblemDetail.of(
            "urn:problem-type:conflict",
            "Konflikt",
            409,
            exception.getMessage());

        return Response.status(409)
            .type(MediaType.valueOf(ProblemMediaType.APPLICATION_PROBLEM_JSON))
            .entity(problem)
            .build();
    }
}
