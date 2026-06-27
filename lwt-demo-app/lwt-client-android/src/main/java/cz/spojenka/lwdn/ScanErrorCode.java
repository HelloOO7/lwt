package cz.spojenka.lwdn;

public enum ScanErrorCode {
    /**
     * Scan failed due to an internal error.
     */
    INTERNAL_ERROR,
    /**
     * Scan failed because the app does not have the required permissions.
     */
    NOT_PERMITTED,
    /**
     * A feature is not supported on this device (e.g. BLE scanning on a device without BLE support).
     */
    NOT_SUPPORTED,
    /**
     * Hardware resources are in short supply.
     */
    OUT_OF_RESOURCES,
    /**
     * Software (OS) has throttled the scan due to too many requests.
     */
    THROTTLED,
    /**
     * Scan is already running.
     */
    ALREADY_RUNNING,
}
