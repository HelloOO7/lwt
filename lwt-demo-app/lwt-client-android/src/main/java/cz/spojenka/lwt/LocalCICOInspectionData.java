package cz.spojenka.lwt;

import android.os.Parcel;
import android.os.Parcelable;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.os.ParcelCompat;

public record LocalCICOInspectionData(
        String tripKey,
        X509Certificate deviceCertificate,
        List<TokenWithExpiration<byte[]>> seedDerivationSecrets
) implements Parcelable {

    public static final Creator<LocalCICOInspectionData> CREATOR = new Creator<>() {
        @Override
        public LocalCICOInspectionData createFromParcel(Parcel in) {
            String tripKey = in.readString();
            X509Certificate cert = ParcelCompat.readSerializable(in, LocalCICOInspectionData.class.getClassLoader(), X509Certificate.class);
            int size = in.readInt();
            List<TokenWithExpiration<byte[]>> secrets = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                secrets.add(new TokenWithExpiration<>(in, in::createByteArray));
            }
            return new LocalCICOInspectionData(tripKey, cert, secrets);
        }

        @Override
        public LocalCICOInspectionData[] newArray(int size) {
            return new LocalCICOInspectionData[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(tripKey());
        dest.writeSerializable(deviceCertificate());
        dest.writeInt(seedDerivationSecrets().size());
        for (TokenWithExpiration<byte[]> seedDerivationSecret : seedDerivationSecrets()) {
            seedDerivationSecret.writeToParcel(dest, dest::writeByteArray);
        }
    }
}
