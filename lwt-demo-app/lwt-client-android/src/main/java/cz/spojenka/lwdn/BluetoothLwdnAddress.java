package cz.spojenka.lwdn;

import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;

public final class BluetoothLwdnAddress implements LwdnAddress {

    private final BluetoothDevice device;
    private final int psm;

    public BluetoothLwdnAddress(BluetoothDevice device, int psm) {
        this.device = device;
        this.psm = psm;
    }

    public BluetoothDevice getDevice() {
        return device;
    }

    public int getPsm() {
        return psm;
    }

    @Override
    public String getLocalHostName() {
        return LwdnAddress.buildHostName("d" + device.getAddress().replace(":", "-"), "bluetooth");
    }

    @Override
    public int getPortNumber() {
        return psm;
    }

    @NonNull
    @Override
    public String toString() {
        return "BluetoothLwdnAddress{" +
                "device=" + device +
                ", psm=" + psm +
                '}';
    }
}
