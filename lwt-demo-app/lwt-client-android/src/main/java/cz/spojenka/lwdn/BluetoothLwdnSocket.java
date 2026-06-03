package cz.spojenka.lwdn;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class BluetoothLwdnSocket implements LwdnSocket {

    private final BluetoothSocket socket;
    private boolean connectInvoked = false;

    public BluetoothLwdnSocket(BluetoothSocket socket) {
        this.socket = socket;
    }

    public BluetoothLwdnSocket(BluetoothDevice device, int psm) throws IOException {
        this(device.createInsecureL2capChannel(psm));
    }

    private void ensureConnected() throws IOException {
        if (!connectInvoked) {
            // do not use socket.isConnected(), as it returns true for a closed socket too
            socket.connect();
            connectInvoked = true;
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        ensureConnected();
        return socket.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        ensureConnected();
        return socket.getOutputStream();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
