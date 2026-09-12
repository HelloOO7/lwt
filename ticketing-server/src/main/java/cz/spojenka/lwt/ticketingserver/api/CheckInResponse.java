package cz.spojenka.lwt.ticketingserver.api;

import java.util.UUID;

public record CheckInResponse(long accountId, UUID sessionId) {
}
