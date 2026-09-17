package cz.spojenka.lwt.cicomock.api.lwtclient;

public class LwtServiceConstants {

    public static final String BLE_DEVICE_NAME = "LWT";

    public static final int BLE_SERVICE_UUID_VEHICLE_BASE = 0x4C575456; // "LWTV"
    public static final int BLE_SERVICE_UUID_STOP_BASE = 0x4C575453; // "LWTS"

    public static final int BLE_SERVICE_UUID_VEHICLE_EXTENDED = BLE_SERVICE_UUID_VEHICLE_BASE + 'E';
    public static final int BLE_SERVICE_UUID_STOP_EXTENDED = BLE_SERVICE_UUID_STOP_BASE + 'E';
}
