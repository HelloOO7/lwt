package cz.spojenka.lwt.inspectionapi;

import android.os.Parcel;
import android.os.Parcelable;

import java.time.Instant;

import androidx.annotation.NonNull;

public class InspectionSecretResponse implements Parcelable {

    public Instant validFrom;
    public Instant validTo;
    public byte[] data;

    public InspectionSecretResponse() {

    }

    protected InspectionSecretResponse(Parcel in) {
        validFrom = Instant.ofEpochMilli(in.readLong());
        validTo = Instant.ofEpochMilli(in.readLong());
        data = in.createByteArray();
    }

    public static final Creator<InspectionSecretResponse> CREATOR = new Creator<>() {
        @Override
        public InspectionSecretResponse createFromParcel(Parcel in) {
            return new InspectionSecretResponse(in);
        }

        @Override
        public InspectionSecretResponse[] newArray(int size) {
            return new InspectionSecretResponse[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeLong(validFrom.toEpochMilli());
        dest.writeLong(validTo.toEpochMilli());
        dest.writeByteArray(data);
    }
}
