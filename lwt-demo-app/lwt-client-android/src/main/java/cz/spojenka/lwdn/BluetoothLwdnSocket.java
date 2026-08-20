package cz.spojenka.lwdn;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class BluetoothLwdnSocket implements LwdnSocket {

    private static final String TAG = "BluetoothLwdnSocket";

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
            IOException connectError = null;
            int numRetries = 0;
            for (int i = 0; i < 3; i++) {
                long connectAttemptStart = SystemClock.elapsedRealtime();
                try {
                    socket.connect();
                    Thread.sleep(50);
                    connectError = null;
                    numRetries = i;
                    break;
                } catch (IOException e) {
                    connectError = e;
                    if (SystemClock.elapsedRealtime() - connectAttemptStart > 1000) {
                        // at this point, it is not likely that a radio instability caused this, it is more likely to be a real timeout
                        break;
                    }
                } catch (InterruptedException ignored) {
                    break;
                }
            }
            connectInvoked = true;
            if (connectError != null) {
                throw connectError;
            }
            if (numRetries > 0) {
                Log.w(TAG, "Needed to retry socket.connect() " + numRetries + " times for successful connection to " + socket.getRemoteDevice().getAddress());
            }
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
    public boolean isOpen() {
        return socket.isConnected();
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
