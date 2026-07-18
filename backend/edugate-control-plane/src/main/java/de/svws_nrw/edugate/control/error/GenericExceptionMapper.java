package de.svws_nrw.edugate.control.error;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

/** Auffangnetz für unerwartete Fehler → 500 Problem+JSON (ohne Interna preiszugeben). */
@Provider
public class GenericExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(GenericExceptionMapper.class);

    @Override
    public Response toResponse(final Throwable exception) {
        LOG.error("Unerwarteter Fehler", exception);

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
