package cz.spojenka.lwdn;

import java.time.Duration;

public class LwdnScanConfig {

    private final Duration timeout;
    private final int maxDevices;
    private final int minRssi;
    private final int maxDistanceMm;
    private final Duration deviceLostTimeout;
    private final ScanMode scanMode;

    private LwdnScanConfig(Duration timeout, int maxDevices, int minRssi, int maxDistanceMm, Duration deviceLostTimeout, ScanMode scanMode) {
        this.timeout = timeout;
        this.maxDevices = maxDevices;
        this.minRssi = minRssi;
        this.maxDistanceMm = maxDistanceMm;
        this.deviceLostTimeout = deviceLostTimeout;
        this.scanMode = scanMode;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public int getMaxDevices() {
        return maxDevices;
    }

    public int getMinRssi() {
        return minRssi;
    }

    public int getMaxDistanceMm() {
        return maxDistanceMm;
    }

    public boolean hasMaxDistance() {
        return maxDistanceMm != Integer.MAX_VALUE;
    }

    public Duration getDeviceLostTimeout() {
        return deviceLostTimeout;
    }

    public ScanMode getScanMode() {
        return scanMode;
    }

    public static class Builder {

        private Duration timeout = Duration.ofSeconds(10);
        private int maxDevices = Integer.MAX_VALUE;
        private int minRssi = -127;
        private int maxDistanceMm = Integer.MAX_VALUE;
        private Duration deviceLostTimeout = Duration.ofSeconds(5);
        private ScanMode scanMode = ScanMode.LOW_LATENCY;

        /**
         * Set a timeout after which the scan will be stopped.
         * The scan will terminate regardless of whether any devices have been found or not.
         *
         * @param timeout the timeout duration
         */
        public Builder setTimeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Set a maximum number of devices that will be returned by the scan.
         * If more devices are found, only the first maxDevices will be returned.
         * The scan will terminate early if maxDevices is reached.
         *
         * @param maxDevices the maximum number of devices
         */
        public Builder setMaxDevices(int maxDevices) {
            this.maxDevices = maxDevices;
            return this;
        }

        /**
         * Set a minimum RSSI value that will be accepted. Devices with lower RSSI will be ignored.
         * This is supported on Bluetooth LE.
         *
         * @param minRssi the minimum RSSI value
         */
        public Builder setMinRssi(int minRssi) {
            this.minRssi = minRssi;
            return this;
        }

        /**
         * Set a maximum distance of the peer from the scanning device that will be accepted.
         * This is supported on Wi-Fi aware.
         *
         * @param maxDistanceMm the maximum distance in millimeters
         */
        public Builder setMaxDistanceMm(int maxDistanceMm) {
            this.maxDistanceMm = maxDistanceMm;
            return this;
        }

        /**
         * Set a timeout after which a device is considered lost if no advertisements
         * are received from it during that period.
         * This is currently only supported on Bluetooth LE. It is not needed on Wi-Fi Aware,
         * as lost peers are detected automatically without the need for a timeout.
         * The default is 5 seconds.
         *
         * @param deviceLostTimeout the timeout duration
         */
        public Builder setDeviceLostTimeout(Duration deviceLostTimeout) {
            this.deviceLostTimeout = deviceLostTimeout;
            return this;
        }

        /**
         * Set whether to prefer low latency when delivering results, or low power consumption.
         * On Bluetooth LE, this chooses between {@link android.bluetooth.le.ScanSettings#SCAN_MODE_LOW_LATENCY}
         * and {@link android.bluetooth.le.ScanSettings#SCAN_MODE_LOW_POWER}. On Wi-Fi Aware and
         * Android 13+, this enables {@link android.net.wifi.aware.SubscribeConfig.Builder#setInstantCommunicationModeEnabled(boolean, int)}
         * if the device supports it.
         *
         * @param scanMode the scan mode
         */
        public Builder setScanMode(ScanMode scanMode) {
            this.scanMode = scanMode;
            return this;
        }

        public LwdnScanConfig build() {
            return new LwdnScanConfig(timeout, maxDevices, minRssi, maxDistanceMm, deviceLostTimeout, scanMode);
        }
    }

    public static enum ScanMode {
        LOW_LATENCY,
        LOW_POWER
    }
}
