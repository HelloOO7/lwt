package cz.spojenka.lwt.ticketing.api;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CICOClearedFare(List<CICOTicketResponse> tickets, List<UUID> sessions, double totalFare, OffsetDateTime clearedAt) implements Serializable {
}
