package cz.spojenka.lwt.ticketingserver.util;

import cz.spojenka.datasources.model.timetable.ConnectionEntity;

import java.time.LocalDate;

public record TripAtDay(ConnectionEntity conn, LocalDate dispatchDay) {
}
