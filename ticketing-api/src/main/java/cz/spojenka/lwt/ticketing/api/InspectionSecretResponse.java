package cz.spojenka.lwt.ticketing.api;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record InspectionSecretResponse(@NotNull Instant validFrom, @NotNull Instant validTo, @NotNull byte[] data) {
}
