package de.svws_nrw.edugate.control.error;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.stream.Collectors;

/** Bean-Validation-Fehler (z. B. leerer Name) → 400 Problem+JSON. */
@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(final ConstraintViolationException exception) {
        final String detail = exception.getConstraintViolations().stream()
            .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
            .collect(Collectors.joining("; "));

        final ProblemDetail problem = ProblemDetail.of(
            "urn:problem-type:validation-error",
            "Validierungsfehler",
            400,
            detail.isBlank() ? "Die Anfrage enthält ungültige Daten." : detail);

        return Response.status(400)
            .type(MediaType.valueOf(ProblemMediaType.APPLICATION_PROBLEM_JSON))
            .entity(problem)
            .build();
    }
}
