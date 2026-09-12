package cz.spojenka.lwt;

import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.core.os.ParcelCompat;

public record LocalCICOInspectionData(
        String tripKey,
        X509Certificate deviceCertificate,
        List<TokenWithExpiration<byte[]>> seedDerivationSecrets,
        Set<UUID> sessionBlacklist
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
            Set<UUID> sessionBlacklist = Arrays
                    .stream(Objects.requireNonNull(in.createTypedArray(ParcelUuid.CREATOR)))
                    .map(ParcelUuid::getUuid)
                    .collect(Collectors.toSet());
            return new LocalCICOInspectionData(tripKey, cert, secrets, sessionBlacklist);
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
        dest.writeTypedArray(sessionBlacklist().stream().map(ParcelUuid::new).toArray(ParcelUuid[]::new), flags);
    }
}
