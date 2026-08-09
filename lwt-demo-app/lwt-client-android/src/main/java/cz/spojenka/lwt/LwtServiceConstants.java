package cz.spojenka.lwt;

import java.util.List;

import cz.spojenka.lwdn.LwdnServiceID;

public class LwtServiceConstants {

    public static final int BLE_SERVICE_UUID_VEHICLE = 0x4C575456; // "LWTV"
    public static final int BLE_SERVICE_UUID_STOP = 0x4C575453; // "LWTS"

    /*
    the extended constants exist so that we can scan for extended services without getting
    results for legacy services also, which should be more efficient.
    Wi-Fi aware supports extended services only, BLE supports both.
     */

    public static final int BLE_SERVICE_UUID_VEHICLE_EXTENDED = BLE_SERVICE_UUID_VEHICLE + 'E';
    public static final int BLE_SERVICE_UUID_STOP_EXTENDED = BLE_SERVICE_UUID_STOP + 'E';

    public static final int BLE_API_PSM = 0xD7;

    public static final String WIFI_AWARE_SERVICE_NAME = "LWT";
    public static final List<LwdnServiceID.MatchingFilterSlot> WIFI_AWARE_MATCHING_FILTERS_VEHICLE = List.of(new LwdnServiceID.MatchingFilterSlot(0, new byte[]{'V'}));
    public static final List<LwdnServiceID.MatchingFilterSlot> WIFI_AWARE_MATCHING_FILTERS_STOP = List.of(new LwdnServiceID.MatchingFilterSlot(1, new byte[]{'S'}));

    public static final int WIFI_API_PORT = 26001;

    public static LwdnServiceID serviceUUIDForDeviceType(LwtDeviceType deviceType) {
        return new LwdnServiceID.UUID(switch (deviceType) {
            case VEHICLE -> BLE_SERVICE_UUID_VEHICLE;
            case STOP -> BLE_SERVICE_UUID_STOP;
        });
    }

    public static LwdnServiceID serviceExtendedUUIDForDeviceType(LwtDeviceType deviceType) {
        return new LwdnServiceID.UUID(switch (deviceType) {
            case VEHICLE -> BLE_SERVICE_UUID_VEHICLE_EXTENDED;
            case STOP -> BLE_SERVICE_UUID_STOP_EXTENDED;
        });
    }

    public static LwdnServiceID serviceNameForDeviceType(LwtDeviceType deviceType) {
        return new LwdnServiceID.ServiceName(WIFI_AWARE_SERVICE_NAME, switch (deviceType) {
            case VEHICLE -> WIFI_AWARE_MATCHING_FILTERS_VEHICLE;
            case STOP -> WIFI_AWARE_MATCHING_FILTERS_STOP;
        });
    }
}
