package cz.spojenka.lwt.ticketingserver.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ImportMetadataRepository extends JpaRepository<ImportMetadata, Integer> {

    @Query("SELECT m FROM ImportMetadata m WHERE m.destination = ?1 ORDER BY m.importTime DESC")
    public ImportMetadata getLatestMetadata(String destination);
}
