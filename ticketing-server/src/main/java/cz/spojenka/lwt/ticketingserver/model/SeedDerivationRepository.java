package cz.spojenka.lwt.ticketingserver.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SeedDerivationRepository extends JpaRepository<SeedDerivationSecret, Long> {

    @Query("SELECT s FROM SeedDerivationSecret s WHERE ?1 >= s.validFrom AND ?1 < s.validTo")
    public SeedDerivationSecret getForTime(long time);

    @Query("SELECT s FROM SeedDerivationSecret s ORDER BY s.validFrom DESC LIMIT 1")
    public SeedDerivationSecret getLatest();
}
