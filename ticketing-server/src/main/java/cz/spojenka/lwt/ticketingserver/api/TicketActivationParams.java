package cz.spojenka.lwt.ticketingserver.api;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;

public record TicketActivationParams(
        @Nullable OffsetDateTime time,
        boolean activateNowIfEarlier,
        boolean clientIntegrityAttested,
        @Nullable String zones,
        @NonNull String appId,
        @Nullable String activationSourceMetadata
) {
}
