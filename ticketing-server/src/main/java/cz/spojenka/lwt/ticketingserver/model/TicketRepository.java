package cz.spojenka.lwt.ticketingserver.model;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {

    @Query("SELECT t FROM Ticket t WHERE t.holderAccount = :account AND (:includePastValidity = true OR t.validUntil IS NULL OR :now <= t.validUntil) ORDER BY t.issuedAt,t.id DESC")
    public Page<Ticket> findAllByHolderAccount(Account account, OffsetDateTime now, boolean includePastValidity, Pageable pageable);
}
