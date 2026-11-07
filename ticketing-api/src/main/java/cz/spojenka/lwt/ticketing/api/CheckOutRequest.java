package cz.spojenka.lwt.ticketing.api;

import java.io.Serializable;
import java.util.UUID;

public record CheckOutRequest(byte[] checkInToken, UUID sessionId) implements Serializable {
}
