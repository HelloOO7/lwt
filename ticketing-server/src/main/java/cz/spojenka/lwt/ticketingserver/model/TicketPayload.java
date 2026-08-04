package cz.spojenka.lwt.ticketingserver.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.jspecify.annotations.NonNull;

@Embeddable
public class TicketPayload {

    @NonNull
    @Column(length = 1024)
    private String etd;
    @NonNull
    private String derivedTotpSeed;

    public TicketPayload(@NonNull String etd, @NonNull String derivedTotpSeed) {
        this.etd = etd;
        this.derivedTotpSeed = derivedTotpSeed;
    }

    TicketPayload() {
        this("", "");
    }

    public @NonNull String getEtd() {
        return etd;
    }

    public @NonNull String getDerivedTotpSeed() {
        return derivedTotpSeed;
    }
}
