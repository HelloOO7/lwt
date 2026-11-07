package cz.spojenka.lwt.ticketingserver.model;

import jakarta.persistence.*;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.TimeZoneStorageType;
import org.jspecify.annotations.NonNull;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
public class CICOClearingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Account payerAccount;
    @TimeZoneStorage(TimeZoneStorageType.AUTO)
    private OffsetDateTime clearedAt;

    @OneToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "clearing_id")
    private List<Ticket> tickets = new ArrayList<>();

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "clearedBy")
    private List<CICOEvent> clearedEvents = new ArrayList<>();

    private double totalCost;

    @NonNull
    private ClearingState state = ClearingState.CLEARED;

    CICOClearingRecord() {

    }

    public CICOClearingRecord(Account payerAccount, OffsetDateTime clearedAt, List<Ticket> tickets, double totalCost) {
        this.payerAccount = payerAccount;
        this.clearedAt = clearedAt;
        this.tickets = tickets;
        this.totalCost = totalCost;
    }

    public long getId() {
        return id;
    }

    public Account getPayerAccount() {
        return payerAccount;
    }

    public OffsetDateTime getClearedAt() {
        return clearedAt;
    }

    public List<Ticket> getTickets() {
        return tickets;
    }

    public List<CICOEvent> getClearedEvents() {
        return clearedEvents;
    }

    public double getTotalCost() {
        return totalCost;
    }

    public ClearingState getState() {
        return state;
    }

    public boolean canBeCancelled() {
        return state != ClearingState.CANCELLED;
    }

    public void cancel() {
        this.state = ClearingState.CANCELLED;
        tickets.forEach(Ticket::cancel);
    }
}
