package cz.spojenka.lwt.ticketing.api;

import java.io.Serializable;
import java.util.List;

public record FareTravelRecord(List<FareSection> sections) implements Serializable {
}
