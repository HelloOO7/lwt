package cz.spojenka.lwt;

import android.os.Parcel;

import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Supplier;

public record TokenWithExpiration<T>(Instant issuedAt, Instant expiresAt, T token) {

    public TokenWithExpiration(Parcel parcel, Supplier<T> tokenReader) {
        this(Instant.ofEpochMilli(parcel.readLong()), Instant.ofEpochMilli(parcel.readLong()), tokenReader.get());
    }

    public boolean isExpired(Instant when) {
        return when.isAfter(expiresAt);
    }

    public void writeToParcel(Parcel parcel, Consumer<T> tokenWriter) {
        parcel.writeLong(issuedAt.toEpochMilli());
        parcel.writeLong(expiresAt.toEpochMilli());
        tokenWriter.accept(token);
    }
}
