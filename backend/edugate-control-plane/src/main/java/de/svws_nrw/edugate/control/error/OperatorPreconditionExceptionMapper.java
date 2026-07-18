package de.svws_nrw.edugate.control.error;

import de.svws_nrw.edugate.control.operator.OperatorPreconditionException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Fachliche Vorbedingung nicht erfüllt (z. B. Verbindungstest ohne Zugangsdaten) → 409 Problem+JSON. */
@Provider
public class OperatorPreconditionExceptionMapper implements ExceptionMapper<OperatorPreconditionException> {

    @Override
    public Response toResponse(final OperatorPreconditionException exception) {
        final ProblemDetail problem = ProblemDetail.of(
            "urn:problem-type:precondition-failed",
            "Vorbedingung nicht erfüllt",
            409,
            exception.getMessage());

        return Response.status(409)
            .type(MediaType.valueOf(ProblemMediaType.APPLICATION_PROBLEM_JSON))
            .entity(problem)
            .build();
    }
}
