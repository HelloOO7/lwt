package cz.spojenka.lwt.ticketingserver.model;

import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;

public interface ITransportEntitlement {

    public Account getHolderAccount();
    @Nullable
    public OffsetDateTime getValidSince();
    @Nullable
    public OffsetDateTime getValidUntil();
    @Nullable
    public String getValidZones();
}
