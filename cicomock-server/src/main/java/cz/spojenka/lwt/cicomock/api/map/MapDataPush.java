package cz.spojenka.lwt.cicomock.api.map;

import cz.spojenka.lwt.cicomock.model.Location;
import cz.spojenka.lwt.cicomock.model.NearbyDevice;
import org.jspecify.annotations.Nullable;

import java.time.ZonedDateTime;
import java.util.List;

public record MapDataPush(
        Location location,
        int trackingConnectionId,
        ZonedDateTime time,
        float timescale,
        List<NearbyDevice> nearbyDevices,
        int presenceTrackingConnectionId,
        @Nullable Float deviceScanRadiusMeters,
        @Nullable Float presenceTrackingRadiusMeters
) {
}
