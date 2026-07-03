package cz.spojenka.lwt;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public enum LwtDeviceType implements Parcelable {
    VEHICLE,
    STOP;

    public static final Creator<LwtDeviceType> CREATOR = new Creator<>() {
        @Override
        public LwtDeviceType createFromParcel(Parcel in) {
            return LwtDeviceType.values()[in.readInt()];
        }

        @Override
        public LwtDeviceType[] newArray(int size) {
            return new LwtDeviceType[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(ordinal());
    }
}
