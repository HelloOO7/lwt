package cz.spojenka.lwdn;

import android.content.Context;
import android.net.ConnectivityManager;

import java.io.Closeable;
import java.io.IOException;

public interface LwdnSocketFactory extends Closeable {

    public LwdnSocket openSocket() throws IOException;

    public static LwdnSocketFactory create(Context context, LwdnAddress address) {
        if (address instanceof BluetoothLwdnAddress bt) {
            return new BluetoothLwdnSocketFactory(bt);
        } else if (address instanceof WifiAwareLwdnAddress wifi) {
            return new WifiAwareLwdnSocketFactory(context.getSystemService(ConnectivityManager.class), wifi);
        } else {
            throw new IllegalArgumentException("Unsupported LWDN address type: " + address.getClass().getName());
        }
    }

    @Override
    public void close();
}
