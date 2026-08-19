package cz.spojenka.lwt.demoapp;

import android.os.Parcel;
import android.os.Parcelable;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.os.ParcelCompat;

public class TicketData implements Parcelable {

    public static final Creator<TicketData> CREATOR = new Creator<>() {
        @Override
        public TicketData createFromParcel(Parcel in) {
            return new TicketData(in);
        }

        @Override
        public TicketData[] newArray(int size) {
            return new TicketData[size];
        }
    };

    private final String tariffSystemId;
    private final int numZones;
    private final List<String> zoneOptions;
    private final Duration validityPeriod;

    private List<String> chosenZones;

    private OffsetDateTime activatedAt;
    private OffsetDateTime validSince;
    private OffsetDateTime validUntil;

    private final byte[] activationToken;

    private byte[] etd;
    private byte[] totpSeed;

    public TicketData(String tariffSystemId, int numZones, List<String> zoneOptions, Duration validityPeriod, byte[] activationToken) {
        this.tariffSystemId = tariffSystemId;
        this.numZones = numZones;
        this.zoneOptions = zoneOptions;
        this.validityPeriod = validityPeriod;
        this.activationToken = activationToken;
    }

    public TicketData(TicketData other) {
        this(other.tariffSystemId, other.numZones, other.zoneOptions, other.validityPeriod, other.activationToken);
        this.chosenZones = other.chosenZones;
        this.activatedAt = other.activatedAt;
        this.validSince = other.validSince;
        this.validUntil = other.validUntil;
        this.etd = other.etd;
        this.totpSeed = other.totpSeed;
    }

    protected TicketData(Parcel in) {
        tariffSystemId = in.readString();
        numZones = in.readInt();
        zoneOptions = in.createStringArrayList();
        validityPeriod = Duration.ofMillis(in.readLong());
        chosenZones = in.createStringArrayList();
        activatedAt = readOffsetDateTime(in);
        validSince = readOffsetDateTime(in);
        validUntil = readOffsetDateTime(in);
        activationToken = in.createByteArray();
        etd = in.createByteArray();
        totpSeed = in.createByteArray();
    }

    private static OffsetDateTime readOffsetDateTime(Parcel in) {
        return ParcelCompat.readSerializable(in, OffsetDateTime.class.getClassLoader(), OffsetDateTime.class);
    }

    public String getTariffSystemId() {
        return tariffSystemId;
    }

    public int getNumZones() {
        return numZones;
    }

    public List<String> getZoneOptions() {
        return zoneOptions;
    }

    public Duration getValidityPeriod() {
        return validityPeriod;
    }

    public OffsetDateTime getActivatedAt() {
        return activatedAt;
    }

    public void setActivatedAt(OffsetDateTime activatedAt) {
        this.activatedAt = activatedAt;
    }

    public OffsetDateTime getValidSince() {
        return validSince;
    }

    public void setValidSince(OffsetDateTime validSince) {
        this.validSince = validSince;
    }

    public OffsetDateTime getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(OffsetDateTime validUntil) {
        this.validUntil = validUntil;
    }

    public List<String> getChosenZones() {
        return chosenZones;
    }

    public void setChosenZones(List<String> chosenZones) {
        this.chosenZones = chosenZones;
    }

    public byte[] getActivationToken() {
        return activationToken;
    }

    public byte[] getEtd() {
        return etd;
    }

    public String getEtdAsString() {
        return new String(etd, StandardCharsets.UTF_8);
    }

    public byte[] getTotpSeed() {
        return totpSeed;
    }

    public void setEtd(byte[] etd) {
        this.etd = etd;
    }

    public void setTotpSeed(byte[] totpSeed) {
        this.totpSeed = totpSeed;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(tariffSystemId);
        dest.writeInt(numZones);
        dest.writeStringList(zoneOptions);
        dest.writeLong(validityPeriod.toMillis());
        dest.writeStringList(chosenZones);
        dest.writeSerializable(activatedAt);
        dest.writeSerializable(validSince);
        dest.writeSerializable(validUntil);
        dest.writeByteArray(activationToken);
        dest.writeByteArray(etd);
        dest.writeByteArray(totpSeed);
    }
}
