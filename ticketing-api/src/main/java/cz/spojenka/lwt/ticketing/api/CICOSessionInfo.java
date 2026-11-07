package cz.spojenka.lwt.ticketing.api;

import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CICOSessionInfo(@NotNull UUID sessionId, @NotNull OffsetDateTime startedAt, OffsetDateTime endedAt, String checkInStop, String checkOutStop, SessionCheckOutType checkOutType) implements Serializable {
}
