package cz.spojenka.lwt.ticketing.api;

import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CICOEventPush(
        @NotNull UUID eventId,
        UUID previousEventId,
        @NotNull UUID sessionId,
        long accountId,
        long localTimestamp,
        OffsetDateTime absoluteTimestamp,
        @NotNull CICOEventType eventType,
        String lwtMetadata
) {

    public CICOEventPush withAbsoluteTimestamp(@NotNull OffsetDateTime absoluteTimestamp) {
        return new CICOEventPush(eventId, previousEventId, sessionId, accountId, localTimestamp, absoluteTimestamp, eventType, lwtMetadata);
    }
}
