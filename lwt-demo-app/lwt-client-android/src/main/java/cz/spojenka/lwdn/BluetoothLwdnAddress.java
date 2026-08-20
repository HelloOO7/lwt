package cz.spojenka.lwdn;

import android.bluetooth.BluetoothDevice;
import android.os.Parcel;

import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.core.os.ParcelCompat;

public final class BluetoothLwdnAddress implements LwdnAddress {

    private final BluetoothDevice device;
    private final int psm;

    public BluetoothLwdnAddress(BluetoothDevice device, int psm) {
        this.device = device;
        this.psm = psm;
    }

    public BluetoothDevice getDevice() {
        return device;
    }

    public int getPsm() {
        return psm;
    }

    @Override
    public String getLocalHostName() {
        return LwdnAddress.buildHostName("d" + device.getAddress().replace(":", "-"), "bluetooth");
    }

    @Override
    public int getPortNumber() {
        return psm;
    }

    @NonNull
    @Override
    public String toString() {
        return "BluetoothLwdnAddress{" +
                "device=" + device +
                ", psm=" + psm +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof BluetoothLwdnAddress that)) return false;
        return psm == that.psm && device.equals(that.device);
    }

    @Override
    public int hashCode() {
        return Objects.hash(device, psm);
    }

    public static final Creator<BluetoothLwdnAddress> CREATOR = new Creator<>() {
        @Override
        public BluetoothLwdnAddress createFromParcel(Parcel in) {
            return new BluetoothLwdnAddress(
                    ParcelCompat.readParcelable(in, BluetoothDevice.class.getClassLoader(), BluetoothDevice.class),
                    in.readInt()
            );
        }

        @Override
        public BluetoothLwdnAddress[] newArray(int size) {
            return new BluetoothLwdnAddress[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeParcelable(device, flags);
        dest.writeInt(psm);
    }
}
