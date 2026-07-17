package de.svws_nrw.edugate.control.error;

import de.svws_nrw.edugate.control.operator.OperatorDeniedException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Fachliche Autorisierungsregel hat die Operation verweigert → 403 Problem+JSON. */
@Provider
public class OperatorDeniedExceptionMapper implements ExceptionMapper<OperatorDeniedException> {

    @Override
    public Response toResponse(final OperatorDeniedException exception) {
        final ProblemDetail problem = ProblemDetail.of(
            "urn:problem-type:denied",
            "Zugriff verweigert",
            403,
            exception.getMessage());

        return Response.status(403)
            .type(MediaType.valueOf(ProblemMediaType.APPLICATION_PROBLEM_JSON))
            .entity(problem)
            .build();
    }
}
