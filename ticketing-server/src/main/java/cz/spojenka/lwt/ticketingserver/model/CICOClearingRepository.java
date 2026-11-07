package cz.spojenka.lwt.ticketingserver.model;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface CICOClearingRepository extends JpaRepository<CICOClearingRecord, Long> {

    @Query("SELECT c FROM CICOClearingRecord c WHERE c.payerAccount.id = :accountId ORDER BY c.clearedAt DESC")
    public Page<CICOClearingRecord> findByAccountId(long accountId, Pageable pageable);

    @Query("SELECT DISTINCT e.sessionId FROM CICOEvent e WHERE e.clearedBy.id = :clearingId")
    public List<UUID> findSessionIDsInClearing(long clearingId);
}
