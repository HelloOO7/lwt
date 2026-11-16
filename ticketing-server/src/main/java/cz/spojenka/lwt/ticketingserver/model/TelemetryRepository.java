package cz.spojenka.lwt.ticketingserver.model;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TelemetryRepository extends JpaRepository<TelemetryEvent, Long> {
}
