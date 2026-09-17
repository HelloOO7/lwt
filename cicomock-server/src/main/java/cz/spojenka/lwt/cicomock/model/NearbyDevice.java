package cz.spojenka.lwt.cicomock.model;

import cz.spojenka.engine.jni.VehicleLinearPosition;
import cz.spojenka.lwt.cicomock.api.lwtclient.TripAdvertisementDataExt;

public record NearbyDevice(
        byte[] address,
        int connectionId,
        String lineName,
        Location location,
        VehicleLinearPosition linearPosition,
        TripAdvertisementDataExt advData
) {
}
