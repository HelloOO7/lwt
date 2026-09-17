package cz.spojenka.lwdn;

import android.net.MacAddress;
import android.os.Parcel;

import java.util.Arrays;

import androidx.annotation.NonNull;

public record MockLwdnAddress(byte[] mac) implements LwdnAddress {

    public static final Creator<MockLwdnAddress> CREATOR = new Creator<>() {

        @Override
        public MockLwdnAddress createFromParcel(Parcel source) {
            return new MockLwdnAddress(source.createByteArray());
        }

        @Override
        public MockLwdnAddress[] newArray(int size) {
            return new MockLwdnAddress[size];
        }
    };

    @Override
    public byte[] getRawLinkAddress() {
        return mac;
    }

    @Override
    public String getLocalHostName() {
        return "";
    }

    @Override
    public int getPortNumber() {
        return 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeByteArray(mac);
    }

    @NonNull
    @Override
    public String toString() {
        return "MockLwdnAddress{" +
                "mac=" + MacAddress.fromBytes(mac).toString() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MockLwdnAddress that)) return false;

        return Arrays.equals(mac, that.mac);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(mac);
    }
}
