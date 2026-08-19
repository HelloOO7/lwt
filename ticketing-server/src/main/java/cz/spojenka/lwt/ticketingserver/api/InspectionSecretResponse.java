package cz.spojenka.lwt.ticketingserver.api;

import java.time.Instant;

public record InspectionSecretResponse(Instant validFrom, Instant validTo, byte[] data) {
}
