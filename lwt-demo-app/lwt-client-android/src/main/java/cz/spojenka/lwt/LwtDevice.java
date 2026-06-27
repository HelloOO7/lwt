package cz.spojenka.lwt;

import androidx.annotation.NonNull;
import cz.spojenka.lwdn.LwdnAddress;

public sealed abstract class LwtDevice permits LwtDevice.Vehicle {

    private final LwdnAddress address;

    public LwtDevice(LwdnAddress address) {
        this.address = address;
    }

    public LwdnAddress getAddress() {
        return address;
    }

    public abstract LwtDeviceType getType();

    public static final class Vehicle extends LwtDevice {

        private final TripAdvertisementData advData;

        public Vehicle(LwdnAddress address, TripAdvertisementData advData) {
            super(address);
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
                    "address=" + getAddress() +
                    ", advData=" + getAdvData() +
                    "} " + super.toString();
        }
    }
}
