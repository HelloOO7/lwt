package cz.spojenka.lwdn;

import android.os.Parcel;
import android.os.ParcelUuid;
import android.os.Parcelable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.core.os.ParcelCompat;

public sealed interface LwdnServiceID extends Parcelable permits LwdnServiceID.UUID, LwdnServiceID.ServiceName {

    public static record UUID(java.util.UUID uuid) implements LwdnServiceID {

        public UUID(int uuid32) {
            // https://stackoverflow.com/questions/13964342/android-how-do-bluetooth-uuids-work
            this(new java.util.UUID((Integer.toUnsignedLong(uuid32) << 32) | 0x1000, 0x800000805f9b34fbL));
        }

        public static final Creator<UUID> CREATOR = new Creator<>() {
            @Override
            public UUID createFromParcel(Parcel in) {
                ParcelUuid parcelUuid = ParcelCompat.readParcelable(in, ParcelUuid.class.getClassLoader(), ParcelUuid.class);
                return new UUID(parcelUuid != null ? parcelUuid.getUuid() : null);
            }

            @Override
            public UUID[] newArray(int size) {
                return new UUID[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(@NonNull Parcel dest, int flags) {
            dest.writeParcelable(new ParcelUuid(uuid()), flags);
        }

        @NonNull
        @Override
        public String toString() {
            return "UUID{" +
                    "uuid=" + uuid +
                    '}';
        }
    }

    public static record ServiceName(String name,
                                     List<MatchingFilterSlot> matchingFilters) implements LwdnServiceID {

        public ServiceName(String name) {
            this(name, List.of());
        }

        public static final Creator<ServiceName> CREATOR = new Creator<>() {
            @Override
            public ServiceName createFromParcel(Parcel in) {
                String name = in.readString();
                List<MatchingFilterSlot> matchingFilters = ParcelCompat.readArrayList(in, MatchingFilterSlot.class.getClassLoader(), MatchingFilterSlot.class);
                return new ServiceName(name, matchingFilters);
            }

            @Override
            public ServiceName[] newArray(int size) {
                return new ServiceName[size];
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
