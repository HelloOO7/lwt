package cz.spojenka.lwdn;

public sealed interface LwdnAddress permits BluetoothLwdnAddress {

    public String getLocalHostName();
    public int getPortNumber();

    public static String buildHostName(String address, String transport) {
        return address + "." + transport + ".lwdn.local";
    }
}
