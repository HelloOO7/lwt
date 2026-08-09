package cz.spojenka.lwdn;

import android.os.Parcelable;

public sealed interface LwdnAddress extends Parcelable permits BluetoothLwdnAddress, WifiAwareLwdnAddress {

    public String getLocalHostName();
    public int getPortNumber();

    public static String buildHostName(String address, String transport) {
        return address + "." + transport + ".lwdn.local";
    }
}
