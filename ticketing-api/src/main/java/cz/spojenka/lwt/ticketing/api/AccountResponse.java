package cz.spojenka.lwt.ticketing.api;

import java.io.Serializable;

public record AccountResponse(long id, String iamEmail, byte[] cicoToken) implements Serializable {
}
