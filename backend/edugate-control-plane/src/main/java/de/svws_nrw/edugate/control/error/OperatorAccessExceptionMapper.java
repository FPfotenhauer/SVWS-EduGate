package de.svws_nrw.edugate.control.error;

import de.svws_nrw.edugate.control.operator.OperatorAccessException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/** Technischer Fehler im Operator-Zugriffspfad → 500 Problem+JSON (ohne Interna preiszugeben). */
@Provider
public class OperatorAccessExceptionMapper implements ExceptionMapper<OperatorAccessException> {

    private static final Logger LOG = Logger.getLogger(OperatorAccessExceptionMapper.class);

    @Override
    public Response toResponse(final OperatorAccessException exception) {
        LOG.error("OperatorAccess-Fehler", exception);

        final ProblemDetail problem = ProblemDetail.of(
            "urn:problem-type:internal-error",
            "Interner Fehler",
            500,
            "Die Anfrage konnte nicht verarbeitet werden.");

        return Response.status(500)
            .type(MediaType.valueOf(ProblemMediaType.APPLICATION_PROBLEM_JSON))
            .entity(problem)
            .build();
    }
}
