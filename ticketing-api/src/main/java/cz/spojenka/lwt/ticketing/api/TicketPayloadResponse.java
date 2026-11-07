package cz.spojenka.lwt.ticketing.api;

import java.io.Serializable;

public record TicketPayloadResponse(byte[] etd, byte[] derivedTotpSeed) implements Serializable {
}
