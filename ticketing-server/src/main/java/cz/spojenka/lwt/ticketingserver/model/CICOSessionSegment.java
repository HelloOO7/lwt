package cz.spojenka.lwt.ticketingserver.model;

import java.util.UUID;

public record CICOSessionSegment(UUID sessionId, CICOEvent checkIn, CICOEvent checkOut) {
}
