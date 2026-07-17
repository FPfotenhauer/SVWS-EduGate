package de.svws_nrw.edugate.control.error;

import de.svws_nrw.edugate.control.operator.OperatorNotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Angefragte Entität existiert nicht → 404 Problem+JSON. */
@Provider
public class OperatorNotFoundExceptionMapper implements ExceptionMapper<OperatorNotFoundException> {

    @Override
    public Response toResponse(final OperatorNotFoundException exception) {
        final ProblemDetail problem = ProblemDetail.of(
            "urn:problem-type:not-found",
            "Nicht gefunden",
            404,
            exception.getMessage());

        return Response.status(404)
            .type(MediaType.valueOf(ProblemMediaType.APPLICATION_PROBLEM_JSON))
            .entity(problem)
            .build();
    }
}
