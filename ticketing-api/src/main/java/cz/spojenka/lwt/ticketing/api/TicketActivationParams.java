package cz.spojenka.lwt.ticketing.api;
import jakarta.validation.constraints.NotNull;

import java.time.OffsetDateTime;

public record TicketActivationParams(
        OffsetDateTime time,
        boolean activateNowIfEarlier,
        boolean clientIntegrityAttested,
        String zones,
        @NotNull String appId,
        String activationSourceMetadata
) {
}
