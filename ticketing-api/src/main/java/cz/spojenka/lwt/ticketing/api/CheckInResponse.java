package cz.spojenka.lwt.ticketing.api;

import java.util.UUID;

public record CheckInResponse(long accountId, UUID sessionId) {
}
