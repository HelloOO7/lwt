package cz.spojenka.lwdn;

import android.bluetooth.BluetoothDevice;

import java.io.IOException;

public class BluetoothLwdnSocketFactory implements LwdnSocketFactory {

    private final BluetoothDevice device;
    private final int psm;

    public BluetoothLwdnSocketFactory(BluetoothLwdnAddress address) {
        this.device = address.getDevice();
        this.psm = address.getPsm();
    }

    @Override
    public LwdnSocket openSocket() throws IOException {
        return new BluetoothLwdnSocket(device, psm);
    }
}
