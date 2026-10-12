package cz.spojenka.lwt.ticketingserver.model;

import jakarta.persistence.*;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;

@Entity
public class Pass implements ITransportEntitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    private int productId;
    @ManyToOne(fetch = FetchType.LAZY)
    private Account holderAccount;

    private OffsetDateTime validSince;
    private OffsetDateTime validUntil;
    @Nullable
    private String validZones;

    Pass() {

    }

    public Pass(int productId, Account holderAccount, @NonNull OffsetDateTime validSince, @NonNull OffsetDateTime validUntil, @Nullable String validZones) {
        this.productId = productId;
        this.holderAccount = holderAccount;
        this.validSince = validSince;
        this.validUntil = validUntil;
        this.validZones = validZones;
    }

    public long getId() {
        return id;
    }

    public int getProductId() {
        return productId;
    }

    @Override
    public Account getHolderAccount() {
        return holderAccount;
    }

    @Override
    @NonNull
    public OffsetDateTime getValidSince() {
        return validSince;
    }

    @Override
    @NonNull
    public OffsetDateTime getValidUntil() {
        return validUntil;
    }

    @Override
    @Nullable
    public String getValidZones() {
        return validZones;
    }
}
