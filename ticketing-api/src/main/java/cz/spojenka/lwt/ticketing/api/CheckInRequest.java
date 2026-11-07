package cz.spojenka.lwt.ticketing.api;

import java.io.Serializable;

public record CheckInRequest(byte[] checkInToken) implements Serializable {
}
