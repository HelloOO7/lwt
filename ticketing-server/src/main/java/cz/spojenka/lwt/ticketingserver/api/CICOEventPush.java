package cz.spojenka.lwt.ticketingserver.api;

import cz.spojenka.lwt.ticketingserver.model.CICOEventType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CICOEventPush(
        @NonNull UUID eventId,
        @Nullable UUID previousEventId,
        @NonNull UUID sessionId,
        int accountId,
        long localTimestamp,
        OffsetDateTime absoluteTimestamp,
        @NonNull CICOEventType eventType,
        String lwtMetadata
) {
}
