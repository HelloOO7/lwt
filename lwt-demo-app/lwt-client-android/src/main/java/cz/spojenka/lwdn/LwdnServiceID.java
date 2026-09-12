package cz.spojenka.lwdn;

import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.os.ParcelCompat;
import cz.spojenka.lwdn.util.BLEScanRecordUtil;

public sealed interface LwdnServiceID extends Parcelable permits LwdnServiceID.BluetoothUUID, LwdnServiceID.AwareServiceName, LwdnServiceID.DeviceName {

    public static record BluetoothUUID(java.util.UUID uuid, boolean isExtended) implements LwdnServiceID {

        public BluetoothUUID(int uuid32, boolean isExtended) {
            // https://stackoverflow.com/questions/13964342/android-how-do-bluetooth-uuids-work
            this(BLEScanRecordUtil.uuid32To128(uuid32), isExtended);
        }

        public static final Creator<BluetoothUUID> CREATOR = new Creator<>() {
            @Override
            public BluetoothUUID createFromParcel(Parcel in) {
                ParcelUuid parcelUuid = ParcelCompat.readParcelable(in, ParcelUuid.class.getClassLoader(), ParcelUuid.class);
                boolean isExtended = ParcelCompat.readBoolean(in);
                return new BluetoothUUID(parcelUuid != null ? parcelUuid.getUuid() : null, isExtended);
            }

            @Override
            public BluetoothUUID[] newArray(int size) {
                return new BluetoothUUID[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeParcelable(new ParcelUuid(uuid()), flags);
            ParcelCompat.writeBoolean(dest, isExtended());
        }

        @NonNull
        @Override
        public String toString() {
            return "UUID{" +
                    "uuid=" + uuid +
                    '}';
        }
    }

    public static record AwareServiceName(String name,
                                          List<MatchingFilterSlot> matchingFilters) implements LwdnServiceID {

        public AwareServiceName(String name) {
            this(name, List.of());
        }

        public static final Creator<AwareServiceName> CREATOR = new Creator<>() {
            @Override
            public AwareServiceName createFromParcel(Parcel in) {
                String name = in.readString();
                List<MatchingFilterSlot> matchingFilters = ParcelCompat.readArrayList(in, MatchingFilterSlot.class.getClassLoader(), MatchingFilterSlot.class);
                return new AwareServiceName(name, matchingFilters);
            }

            @Override
            public AwareServiceName[] newArray(int size) {
                return new AwareServiceName[size];
            }
        };

        public List<byte[]> compileMatchingFilters() {
            List<byte[]> filters = new ArrayList<>();
            for (MatchingFilterSlot slot : matchingFilters) {
                while (filters.size() <= slot.slotIndex()) {
                    filters.add(new byte[]{-1}); // all unspecified slots will only match if publisher has a [] there
                }
                filters.set(slot.slotIndex(), slot.filter());
            }
            return filters;
        }

        public boolean checkFilterMatched(List<byte[]> matchingFilter) {
            if (this.matchingFilters().isEmpty()) {
                return true;
            }
            boolean anyMatch = false;
            for (MatchingFilterSlot slot : this.matchingFilters()) {
                /*
                If the number of <length, value> pairs in the Matching Filter field of the Service Descriptor attribute is less than or equal to
                the number of <length, value> pairs in the matching_filter_rx, and there is a match declared for each <length, value> pair
                in the Matching Filter field of the Service Descriptor attribute, a trigger condition is met; otherwise, a trigger condition is not
                met.
                 */
                if (slot.slotIndex() < matchingFilter.size()) {
                    byte[] published = matchingFilter.get(slot.slotIndex());
                    if (published.length > 0 && slot.filter().length > 0 && !Arrays.equals(published, slot.filter())) {
                        return false;
                    } else {
                        anyMatch = true;
                    }
                }
            }
            return matchingFilter.isEmpty() || anyMatch;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeString(name);
            dest.writeList(matchingFilters);
        }

        @NonNull
        @Override
        public String toString() {
            return "ServiceName{" +
                    "name='" + name + '\'' +
                    ", matchingFilters=" + Arrays.deepToString(compileMatchingFilters().toArray()) +
                    '}';
        }
    }

    /**
     * Special service ID that is used by BLE scanner on devices where service UUID filtering
     * is broken. On those devices, filtering will be done by name, and the UUID will be processed
     * in software by the scanner after discovery.
     *
     * @param name the name of the device
     */
    public static record DeviceName(String name) implements LwdnServiceID {

        public static final Creator<DeviceName> CREATOR = new Creator<>() {
            @Override
            public DeviceName createFromParcel(Parcel in) {
                return new DeviceName(in.readString());
            }

            @Override
            public DeviceName[] newArray(int size) {
                return new DeviceName[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeString(name);
        }

        @Override
        public String toString() {
            return "DeviceName{" +
                    "name='" + name + '\'' +
                    '}';
        }
    }

    public static record MatchingFilterSlot(int slotIndex, byte[] filter) implements Parcelable {

        private MatchingFilterSlot(Parcel in) {
            this(in.readInt(), in.createByteArray());
        }

        public static final Creator<MatchingFilterSlot> CREATOR = new Creator<>() {
            @Override
            public MatchingFilterSlot createFromParcel(Parcel in) {
                return new MatchingFilterSlot(in);
            }

            @Override
            public MatchingFilterSlot[] newArray(int size) {
                return new MatchingFilterSlot[size];
            }
        };

        @NonNull
        @Override
        public String toString() {
            return "MatchingFilterSlot{" +
                    "slotIndex=" + slotIndex +
                    ", filter=" + Arrays.toString(filter) +
                    '}';
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeInt(slotIndex);
            dest.writeByteArray(filter);
        }
    }
}
