package cz.spojenka.lwt.ticketing.api;

import java.io.Serializable;

public record CICOTicketResponse(TicketResponse ticket, FareTravelRecord travelRecord) implements Serializable {
}
