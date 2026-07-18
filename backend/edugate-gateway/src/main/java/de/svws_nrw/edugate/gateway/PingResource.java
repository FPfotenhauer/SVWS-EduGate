package de.svws_nrw.edugate.gateway;

import io.quarkus.security.Authenticated;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Reiner Erreichbarkeits-/Token-Check für den Gateway-Service (Skeleton, ADR-004). Verlangt ein
 * gültiges Bearer-Token, aber keine bestimmte Rolle – API-Clients (Client-Credentials-Flow,
 * ADR-005) müssen keine Admin-Rolle besitzen, nur ein gültiges Token.
 */
@Path("/gateway/api/v1/ping")
@Authenticated
public class PingResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public PingResponse ping() {
        return new PingResponse("ok");
    }

    public record PingResponse(String status) {
    }
}
