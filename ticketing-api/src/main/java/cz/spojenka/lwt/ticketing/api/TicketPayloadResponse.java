package cz.spojenka.lwt.ticketing.api;

public record TicketPayloadResponse(byte[] etd, byte[] derivedTotpSeed) {
}
