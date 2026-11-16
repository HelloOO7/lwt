package cz.spojenka.lwt.ticketing.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.io.Serializable;
import java.time.OffsetDateTime;

public record TelemetryEventPush(
        TelemetryEventClass eventClass,
        @Size(max = 64)
        String senderId,
        long localTimestamp,
        OffsetDateTime absoluteTimestamp,
        @Size(max = 32)
        String eventType,
        @Size(max = 256)
        String eventParameter,
        Object extraData
) implements IEventPush, Serializable {

    public TelemetryEventPush withAbsoluteTimestamp(@NotNull OffsetDateTime absoluteTimestamp) {
        return new TelemetryEventPush(eventClass, senderId, localTimestamp, absoluteTimestamp, eventType, eventParameter, extraData);
    }

    public TelemetryEventPush withSenderId(@NotNull String senderId) {
        return new TelemetryEventPush(eventClass, senderId, localTimestamp, absoluteTimestamp, eventType, eventParameter, extraData);
    }
}
