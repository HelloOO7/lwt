package cz.spojenka.lwt.ticketingserver.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.apache.commons.lang3.ArrayUtils;
import org.jspecify.annotations.NonNull;

@Embeddable
public class TicketPayload {

    @Column(length = 1024)
    private byte @NonNull [] etd;
    private byte @NonNull [] derivedTotpSeed;

    public TicketPayload(byte @NonNull [] etd, byte @NonNull [] derivedTotpSeed) {
        this.etd = etd;
        this.derivedTotpSeed = derivedTotpSeed;
    }

    TicketPayload() {
        this(ArrayUtils.EMPTY_BYTE_ARRAY, ArrayUtils.EMPTY_BYTE_ARRAY);
    }

    public byte @NonNull [] getEtd() {
        return etd;
    }

    public byte @NonNull [] getDerivedTotpSeed() {
        return derivedTotpSeed;
    }
}
