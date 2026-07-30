package cz.spojenka.lwt.demoapp;

import android.os.Parcel;
import android.os.Parcelable;

import java.time.Duration;
import java.util.List;

import androidx.annotation.NonNull;

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

    private final byte[] activationToken;
    private final byte[] activationTokenHashSigned;

    public TicketData(String tariffSystemId, int numZones, List<String> zoneOptions, Duration validityPeriod, byte[] activationToken, byte[] activationTokenHashSigned) {
        this.tariffSystemId = tariffSystemId;
        this.numZones = numZones;
        this.zoneOptions = zoneOptions;
        this.validityPeriod = validityPeriod;
        this.activationToken = activationToken;
        this.activationTokenHashSigned = activationTokenHashSigned;
    }

    protected TicketData(Parcel in) {
        tariffSystemId = in.readString();
        numZones = in.readInt();
        zoneOptions = in.createStringArrayList();
        validityPeriod = Duration.ofMillis(in.readLong());
        chosenZones = in.createStringArrayList();
        activationToken = in.createByteArray();
        activationTokenHashSigned = in.createByteArray();
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

    public void setChosenZones(List<String> chosenZones) {
        this.chosenZones = chosenZones;
    }

    public byte[] getActivationToken() {
        return activationToken;
    }

    public byte[] getActivationTokenHashSigned() {
        return activationTokenHashSigned;
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
        dest.writeByteArray(activationToken);
        dest.writeByteArray(activationTokenHashSigned);
    }
}
