package cz.spojenka.lwdn;

import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import androidx.annotation.NonNull;
import androidx.core.os.ParcelCompat;

public record LwdnScanResult(
        LwdnAddress deviceAddress,
        int rssi,
        Map<UUID, byte[]> serviceData
) implements Parcelable {

    static final Creator<LwdnScanResult> CREATOR = new Creator<>() {
        @Override
        public LwdnScanResult createFromParcel(Parcel in) {
            LwdnAddress address = ParcelCompat.readParcelable(in, LwdnAddress.class.getClassLoader(), LwdnAddress.class);
            int rssi = in.readInt();
            int serviceDataSize = in.readInt();
            Map<UUID, byte[]> serviceData = new HashMap<>();
            for (int i = 0; i < serviceDataSize; i++) {
                ParcelUuid parcelUuid = ParcelCompat.readParcelable(in, ParcelUuid.class.getClassLoader(), ParcelUuid.class);
                byte[] data = in.createByteArray();
                serviceData.put(parcelUuid != null ? parcelUuid.getUuid() : null, data);
            }
            return new LwdnScanResult(address, rssi, serviceData);
        }

        @Override
        public LwdnScanResult[] newArray(int size) {
            return new LwdnScanResult[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeParcelable(deviceAddress, flags);
        dest.writeInt(rssi);
        dest.writeInt(serviceData.size());
        for (Map.Entry<UUID, byte[]> entry : serviceData.entrySet()) {
            dest.writeParcelable(new ParcelUuid(entry.getKey()), flags);
            dest.writeByteArray(entry.getValue());
        }
    }
}
