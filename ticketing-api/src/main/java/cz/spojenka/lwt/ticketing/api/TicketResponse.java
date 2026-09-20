package cz.spojenka.lwt.ticketing.api;

import java.time.OffsetDateTime;

public record TicketResponse(
    long id,
    int productId,
    long holderAccountId,
    OffsetDateTime validSince,
    OffsetDateTime validUntil,
    String validZones,
    byte[] activationToken,
    OffsetDateTime activationTime,
    String activationAppId,
    TicketPayloadResponse payload
) {
}
