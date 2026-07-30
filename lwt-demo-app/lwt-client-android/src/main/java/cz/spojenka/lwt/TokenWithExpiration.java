package cz.spojenka.lwt;

import java.time.Instant;

public record TokenWithExpiration<T>(Instant issuedAt, Instant expiresAt, T token) {

    public boolean isExpired(Instant when) {
        return when.isAfter(expiresAt);
    }
}
