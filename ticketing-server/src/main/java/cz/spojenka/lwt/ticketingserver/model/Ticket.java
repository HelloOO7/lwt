package cz.spojenka.lwt.ticketingserver.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.OffsetDateTime;

@Entity
public class Ticket implements ITransportEntitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Product product;
    @ManyToOne(fetch = FetchType.LAZY)
    private Account holderAccount;
    @OneToOne(optional = true, orphanRemoval = false, cascade = CascadeType.PERSIST)
    @Nullable
    private OrderItem orderedBy;

    @Embedded
    private TicketActivationToken activationToken;

    @Embedded
    @Nullable
    private TicketPayload payload;

    private Instant issuedAt;
    @Nullable
    private OffsetDateTime validSince;
    @Nullable
    private OffsetDateTime validUntil;
    @Nullable
    private String validZones;

    @Nullable
    @Embedded
    public TicketActivationRecord activationRecord;

    @NonNull
    private TicketState state = TicketState.ISSUED;

    Ticket() {

    }

    public Ticket(Product product, Account holderAccount) {
        this(product, holderAccount, null);
    }

    public Ticket(Product product, Account holderAccount, @Nullable OrderItem orderedBy) {
        this.product = product;
        this.holderAccount = holderAccount;
        this.orderedBy = orderedBy;
        this.issuedAt = Instant.now();
    }

    public long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    @Override
    @JsonIgnore
    public Account getHolderAccount() {
        return holderAccount;
    }

    public long getHolderAccountId() {
        return holderAccount.getId();
    }

    public @Nullable OrderItem getOrderedBy() {
        return orderedBy;
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

    public Instant getIssuedAt() {
        return issuedAt;
    }

    @Override
    @Nullable
    public OffsetDateTime getValidSince() {
        return validSince;
    }

    public void setValidSince(@Nullable OffsetDateTime validSince) {
        this.validSince = validSince;
    }

    @Override
    @Nullable
    public OffsetDateTime getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(@Nullable OffsetDateTime validUntil) {
        this.validUntil = validUntil;
    }

    @Override
    @Nullable
    public String getValidZones() {
        return validZones;
    }

    @Nullable
    public TicketActivationRecord getActivationRecord() {
        return activationRecord;
    }

    public @NonNull TicketState getState() {
        return state;
    }

    public void activate(OffsetDateTime validSince, String validZones, TicketActivationRecord activationRecord) {
        this.validSince = validSince;
        this.validUntil = this.validSince.plusMinutes(60); //todo - take from product
        this.validZones = validZones;
        this.activationRecord = activationRecord;
    }

    public boolean canBeCancelledByUser() {
        return state == TicketState.ISSUED;
    }

    public void cancel() {
        this.state = TicketState.CANCELLED;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("product", product)
                .append("holderAccount", holderAccount.getId())
                .append("orderedBy", orderedBy)
                .append("activationToken", activationToken)
                .append("payload", payload)
                .append("validSince", validSince)
                .append("validUntil", validUntil)
                .append("validZones", validZones)
                .append("activationRecord", activationRecord)
                .toString();
    }
}
