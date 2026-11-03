package cz.spojenka.lwt.ticketingserver.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import cz.spojenka.datasources.persistentids.ParameterLine;

import java.time.LocalDate;
import java.time.LocalTime;

public class LwtTicketMetadata {

    @JsonProperty("TK")
    public String tripKey;
    @JsonProperty("TTD")
    public LocalTime timetableDeparture;
    @JsonProperty("TTA")
    public LocalTime timetableArrival;
    @JsonProperty("S")
    public int stopCis;
    @JsonProperty("LS")
    public LocationStateCode locationStateCode;

    public static LwtTicketMetadata parse(String data) {
        return parse(data, LwtTicketMetadata.class);
    }

    public static <T extends LwtTicketMetadata> T parse(String data, Class<T> clazz) {
        return ParameterLine.withSeparator('|').decodePOJO(data, clazz);
    }

    public static enum LocationStateCode {
        @JsonProperty(".")
        AT_STOP,
        @JsonProperty(">")
        BEFORE_STOP,
        @JsonProperty("<")
        AFTER_STOP,
        @JsonProperty("=")
        BETWEEN_STOPS
    }
}
