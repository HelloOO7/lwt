package cz.spojenka.lwdn;

import android.os.Parcelable;

import androidx.annotation.Nullable;

public sealed interface LwdnAddress extends Parcelable permits BluetoothLwdnAddress, WifiAwareLwdnAddress {

    @Nullable
    public byte[] getRawLinkAddress();
    public String getLocalHostName();
    public int getPortNumber();

    public static String buildHostName(String address, String transport) {
        return address + "." + transport + ".lwdn.local";
    }
}
