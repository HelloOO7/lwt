package cz.spojenka.lwt.ticketingserver.model;

import com.fasterxml.jackson.annotation.JsonFormat;

@JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
public enum CsvValidityType {
    NULL,
    REGULAR,
    TO_MIDNIGHT
}
