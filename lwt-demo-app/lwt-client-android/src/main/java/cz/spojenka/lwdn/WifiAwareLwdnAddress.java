package cz.spojenka.lwdn;

import android.net.wifi.aware.DiscoverySession;
import android.net.wifi.aware.PeerHandle;
import android.os.Parcel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class WifiAwareLwdnAddress implements LwdnAddress {

    private static final NonParcelableDataManager NON_PARCELABLE_MANAGER = new NonParcelableDataManager();

    public static final Creator<WifiAwareLwdnAddress> CREATOR = new Creator<>() {
        @Override
        public WifiAwareLwdnAddress createFromParcel(Parcel in) {
            return new WifiAwareLwdnAddress(in);
        }

        @Override
        public WifiAwareLwdnAddress[] newArray(int size) {
            return new WifiAwareLwdnAddress[size];
        }
    };

    private final NonParcelableData data;
    private final int dataId;
    private final byte[] macAddress;
    private final int port;

    private WifiAwareLwdnAddress(DiscoverySession discoverySession, PeerHandle peerHandle, byte[] macAddress, int port) {
        data = new NonParcelableData(discoverySession, peerHandle);
        dataId = NON_PARCELABLE_MANAGER.store(data);
        this.macAddress = macAddress;
        this.port = port;
    }

    private WifiAwareLwdnAddress(Parcel in) {
        dataId = in.readInt();
        data = NON_PARCELABLE_MANAGER.retrieve(dataId);
        macAddress = in.createByteArray();
        port = in.readInt();
    }

    public WifiAwareLwdnAddress withKnownAddress(byte[] macAddress) {
        return new WifiAwareLwdnAddress(data.discoverySession(), data.peerHandle(), macAddress, this.port);
    }

    public boolean isValid() {
        return data != null;
    }

    public DiscoverySession getDiscoverySession() {
        return data.discoverySession();
    }

    public PeerHandle getPeerHandle() {
        return data.peerHandle();
    }

    @Nullable
    @Override
    public byte[] getRawLinkAddress() {
        return macAddress;
    }

    @Override
    public String getLocalHostName() {
        return LwdnAddress.buildHostName("default", "wifi");
    }

    @Override
    public int getPortNumber() {
        return port;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(dataId);
        dest.writeByteArray(macAddress);
        dest.writeInt(port);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof WifiAwareLwdnAddress that)) return false;

        if (macAddress != null && that.macAddress != null) {
            return Arrays.equals(macAddress, that.macAddress);
        }

        if ((data == null) != (that.data == null)) {
            return false;
        } else {
            if (data != null) {
                return Objects.equals(data.peerHandle(), that.data.peerHandle());
            } else {
                return this.dataId == that.dataId;
            }
        }
    }

    @Override
    public int hashCode() {
        int result = data != null ? Objects.hashCode(data.peerHandle()) : dataId;
        result = 31 * result + Arrays.hashCode(macAddress);
        return result;
    }

    @NonNull
    @Override
    public String toString() {
        return "WifiAwareLwdnAddress{" +
                "peerHandle=" + (data != null ? data.peerHandle() : null) +
                ", macAddress=" + Arrays.toString(macAddress) +
                ", port=" + port +
                '}';
    }

    public static WifiAwareLwdnAddress create(DiscoverySession discoverySession, PeerHandle peerHandle, int port) {
        return new WifiAwareLwdnAddress(discoverySession, peerHandle, null, port);
    }

    public static void onSessionTerminated(DiscoverySession discoverySession) {
        NON_PARCELABLE_MANAGER.onSessionTerminated(discoverySession);
    }

    private static record NonParcelableData(DiscoverySession discoverySession,
                                            PeerHandle peerHandle) {

    }

    private static class NonParcelableDataManager {

        private final Map<DiscoverySession, List<Integer>> addrsInSessions = new HashMap<>();
        private final Map<Integer, NonParcelableData> datasById = new HashMap<>();

        private int idCounter = 1;

        public int store(NonParcelableData data) {
            int id = idCounter++;
            datasById.put(id, data);
            addrsInSessions.computeIfAbsent(data.discoverySession(), k -> new ArrayList<>()).add(id);
            return id;
        }

        public NonParcelableData retrieve(int id) {
            return datasById.get(id);
        }

        public void onSessionTerminated(DiscoverySession discoverySession) {
            List<Integer> ids = addrsInSessions.remove(discoverySession);
            if (ids != null) {
                for (int id : ids) {
                    datasById.remove(id);
                }
            }
        }
    }
}
