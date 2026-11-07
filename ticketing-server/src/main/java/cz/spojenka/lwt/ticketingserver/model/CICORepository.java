package cz.spojenka.lwt.ticketingserver.model;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public interface CICORepository extends JpaRepository<CICOEvent, UUID> {

    @Query("SELECT e FROM CICOEvent e WHERE e.sessionId = ?1 ORDER BY e.eventTime DESC LIMIT 1")
    public CICOEvent findNewestEventInSession(UUID sessionId);

    @Query("SELECT e FROM CICOEvent e WHERE e.sessionId = ?1 ORDER BY e.eventTime ASC")
    public List<CICOEvent> findAllInSession(UUID sessionId);
    
    @Query(value = """
            SELECT DISTINCT new cz.spojenka.lwt.ticketingserver.model.CICOSessionSegment(
                e.sessionId,
                ci,
                co
            )
            FROM CICOEvent e
            LEFT JOIN CICOEvent ci ON ci.sessionId = e.sessionId
                AND ci.account.id = :accountId
                AND ci.id = (
                    SELECT ciCandidate.id
                    FROM CICOEvent ciCandidate
                    WHERE ciCandidate.sessionId = e.sessionId
                        AND ciCandidate.account.id = :accountId
                        AND ciCandidate.eventType = cz.spojenka.lwt.ticketing.api.CICOEventType.CHECK_IN
                    ORDER BY ciCandidate.eventTime ASC, ciCandidate.id ASC
                    LIMIT 1
                )
            LEFT JOIN CICOEvent co ON co.sessionId = e.sessionId
                AND co.account.id = :accountId
                AND co.id = (
                    SELECT coCandidate.id
                    FROM CICOEvent coCandidate
                    WHERE coCandidate.sessionId = e.sessionId
                        AND coCandidate.account.id = :accountId
                        AND (
                                coCandidate.eventType = cz.spojenka.lwt.ticketing.api.CICOEventType.CHECK_OUT
                                OR
                                coCandidate.eventType = cz.spojenka.lwt.ticketing.api.CICOEventType.BE_OUT
                        )
                    ORDER BY coCandidate.eventTime DESC, coCandidate.id ASC
                    LIMIT 1
                )
            WHERE e.account.id = :accountId
            AND (:unclearedOnly = false OR e.clearedBy IS NULL)
            ORDER BY ci.eventTime DESC, ci.id DESC
            """,
            countQuery = """
            SELECT COUNT(DISTINCT e.sessionId)
            FROM CICOEvent e
            WHERE e.account.id = :accountId
            AND (:unclearedOnly = false OR e.clearedBy IS NULL)
            """)
    public Page<CICOSessionSegment> findSessionsByAccountId(long accountId, boolean unclearedOnly, Pageable pageable);

    public default Page<CICOSessionSegment> findSessionsByAccountId(long accountId, Pageable pageable) {
        return findSessionsByAccountId(accountId, false, pageable);
    }

    public default List<CICOSessionSegment> findUnclearedSessionsByAccountId(long accountId) {
        return findSessionsByAccountId(accountId, true, Pageable.unpaged()).getContent();
    }

    @Query("SELECT DISTINCT e.account FROM CICOEvent e WHERE e.clearedBy IS NULL")
    public Stream<Account> findAccountsWithUnclearedEvents();

    @Query("SELECT e FROM CICOEvent e WHERE e.account.id = :accountId AND e.clearedBy IS NULL ORDER BY e.eventTime ASC")
    public List<CICOEvent> getUnclearedEventsForAccount(long accountId);
}
