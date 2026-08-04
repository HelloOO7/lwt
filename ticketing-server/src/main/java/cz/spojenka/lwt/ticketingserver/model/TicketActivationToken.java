package cz.spojenka.lwt.ticketingserver.model;

import jakarta.persistence.Embeddable;
import org.jspecify.annotations.NonNull;

@Embeddable
public class TicketActivationToken {

    private byte @NonNull [] activationToken;

    public TicketActivationToken(byte @NonNull [] activationToken) {
        this.activationToken = activationToken;
    }

    TicketActivationToken() {
        this(new byte[0]);
    }

    public byte @NonNull [] getActivationToken() {
        return activationToken;
    }
}
