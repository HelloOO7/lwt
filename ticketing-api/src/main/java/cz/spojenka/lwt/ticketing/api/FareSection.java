package cz.spojenka.lwt.ticketing.api;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

public record FareSection(String lineName, String tripNumber, Point from, Point to) implements Serializable {

    public static record Point(String stopName, OffsetDateTime time, List<String> tariffZones) implements Serializable {

    }
}
