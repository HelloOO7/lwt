package cz.spojenka.lwdn;

import java.io.IOException;

public interface LwdnSocketFactory {

    public LwdnSocket openSocket() throws IOException;

    public static LwdnSocketFactory create(LwdnAddress address) {
        if (address instanceof BluetoothLwdnAddress bt) {
            return new BluetoothLwdnSocketFactory(bt);
        } else {
            throw new IllegalArgumentException("Unsupported LWDN address type: " + address.getClass().getName());
        }
    }
}
