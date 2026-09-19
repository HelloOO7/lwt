package cz.spojenka.lwt.ticketingserver.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AccountRepository extends JpaRepository<Account, Long> {

    @Query("SELECT a FROM Account a JOIN a.identities i WHERE i.issuer = ?1 AND i.subject = ?2")
    public Account findByIdentity(String issuer, String subject);
}
