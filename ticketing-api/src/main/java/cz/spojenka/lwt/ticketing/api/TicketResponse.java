package cz.spojenka.lwt.ticketing.api;

import java.io.Serializable;
import java.time.OffsetDateTime;

public record TicketResponse(
    long id,
    ProductResponse product,
    long holderAccountId,
    Long orderId,
    OffsetDateTime validSince,
    OffsetDateTime validUntil,
    String validZones,
    byte[] activationToken,
    OffsetDateTime activationTime,
    String activationAppId,
    TicketPayloadResponse payload
) implements Serializable {
}
