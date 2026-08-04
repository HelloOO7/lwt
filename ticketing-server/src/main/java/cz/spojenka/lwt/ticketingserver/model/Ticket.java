package cz.spojenka.lwt.ticketingserver.model;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;

@Entity
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private int productId;
    private long holderAccountId;

    @Embedded
    private TicketActivationToken activationToken;

    @Embedded
    @Nullable
    private TicketPayload payload;

    @Nullable
    private OffsetDateTime validSince;
    @Nullable
    private OffsetDateTime validUntil;
    @Nullable
    private String validZones;

    @Nullable
    @Embedded
    public TicketActivationRecord activationRecord;

    Ticket() {

    }

    public Ticket(int productId, long holderAccountId) {
        this.productId = productId;
        this.holderAccountId = holderAccountId;
    }

    public long getId() {
        return id;
    }

    public int getProductId() {
        return productId;
    }

    public long getHolderAccountId() {
        return holderAccountId;
    }

    @Nullable
    public TicketPayload getPayload() {
        return payload;
    }

    public TicketActivationToken getActivationToken() {
        return activationToken;
    }

    public void setActivationToken(TicketActivationToken activationToken) {
        this.activationToken = activationToken;
    }

    public void setPayload(@Nullable TicketPayload payload) {
        this.payload = payload;
    }

    @Nullable
    public OffsetDateTime getValidSince() {
        return validSince;
    }

    @Nullable
    public OffsetDateTime getValidUntil() {
        return validUntil;
    }

    @Nullable
    public String getValidZones() {
        return validZones;
    }

    @Nullable
    public TicketActivationRecord getActivationRecord() {
        return activationRecord;
    }

    public void activate(OffsetDateTime validSince, String validZones, TicketActivationRecord activationRecord) {
        this.validSince = validSince;
        this.validUntil = this.validSince.plusMinutes(60); //todo - take from product
        this.validZones = validZones;
        this.activationRecord = activationRecord;
    }
}
