package cz.spojenka.lwt;

import androidx.annotation.NonNull;
import cz.spojenka.lwdn.LwdnAddress;
import cz.spojenka.lwdn.LwdnScanResult;

public sealed abstract class LwtDevice permits LwtDevice.Vehicle {

    private final LwdnScanResult scanResult;

    public LwtDevice(LwdnScanResult scanResult) {
        this.scanResult = scanResult;
    }

    public LwdnAddress getAddress() {
        return scanResult.deviceAddress();
    }

    public LwdnScanResult getScanResult() {
        return scanResult;
    }

    public abstract LwtDeviceType getType();

    public static final class Vehicle extends LwtDevice {

        private final TripAdvertisementData advData;

        public Vehicle(LwdnScanResult scanResult, TripAdvertisementData advData) {
            super(scanResult);
            this.advData = advData;
        }

        @Override
        public LwtDeviceType getType() {
            return LwtDeviceType.VEHICLE;
        }

        public TripAdvertisementData getAdvData() {
            return advData;
        }

        @NonNull
        @Override
        public String toString() {
            return "Vehicle{" +
                    "scanResult=" + getScanResult() +
                    ", advData=" + getAdvData() +
                    "} " + super.toString();
        }
    }
}
