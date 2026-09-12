package cz.spojenka.lwt.ticketingserver.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.UUID;

public interface CICORepository extends JpaRepository<CICOEvent, UUID> {

    @Query("SELECT e FROM CICOEvent e WHERE e.sessionId = ?1 ORDER BY e.eventTime DESC LIMIT 1")
    public CICOEvent findNewestEventInSession(UUID sessionId);
}
