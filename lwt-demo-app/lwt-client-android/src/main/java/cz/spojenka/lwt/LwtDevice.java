package cz.spojenka.lwt;

import android.os.Parcel;
import android.os.Parcelable;

import java.io.IOException;

import androidx.annotation.NonNull;
import cz.spojenka.lwdn.LwdnAddress;
import cz.spojenka.lwdn.LwdnScanResult;

public sealed abstract class LwtDevice implements Parcelable permits LwtDevice.Vehicle {

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

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeParcelable(getScanResult(), flags);
            try {
                dest.writeByteArray(TripAdvertisementData.wrap(getAdvData()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        static final Parcelable.Creator<Vehicle> CREATOR = new Parcelable.Creator<>() {
            @Override
            public Vehicle createFromParcel(Parcel source) {
                LwdnScanResult scanResult = source.readParcelable(LwdnScanResult.class.getClassLoader());
                byte[] advDataBytes = source.createByteArray();
                TripAdvertisementData advData;
                try {
                    advData = TripAdvertisementData.unwrap(advDataBytes);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return new Vehicle(scanResult, advData);
            }

            @Override
            public Vehicle[] newArray(int size) {
                return new Vehicle[size];
            }
        };
    }
}
