package cz.spojenka.lwt;

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

    public static int serviceUUIDForDeviceType(LwtDeviceType deviceType) {
        return switch (deviceType) {
            case VEHICLE -> BLE_SERVICE_UUID_VEHICLE;
            case STOP -> BLE_SERVICE_UUID_STOP;
        };
    }

    public static int serviceExtendedUUIDForDeviceType(LwtDeviceType deviceType) {
        return switch (deviceType) {
            case VEHICLE -> BLE_SERVICE_UUID_VEHICLE_EXTENDED;
            case STOP -> BLE_SERVICE_UUID_STOP_EXTENDED;
        };
    }
}
