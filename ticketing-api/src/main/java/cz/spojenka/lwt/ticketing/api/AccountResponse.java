package cz.spojenka.lwt.ticketing.api;

public record AccountResponse(long id, String iamEmail, byte[] cicoToken) {
}
