package cz.spojenka.lwt.ticketing.api;

import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDate;

public record ProductResponse(
        int id,
        ProductCategory category,
        String title,
        float price,
        float vatRate,
        LocalDate validFrom,
        LocalDate validTo,
        Duration validityDuration,
        ProductValidityType validityType,
        Integer zoneCount,
        String eligibleZones,
        String excludedZones,
        boolean excludesTrains,
        String notice
) implements Serializable {
}
