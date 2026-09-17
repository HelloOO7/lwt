package cz.spojenka.lwt.cicomock.api.map;

import cz.spojenka.lwt.cicomock.model.Location;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;

public record MapChangeRequest(
        @Nullable Location newLocation,
        @Nullable ZonedDateTime newTime,
        @Nullable Float newTimescale,
        @Nullable Integer newTrackingConnection
) {
}
