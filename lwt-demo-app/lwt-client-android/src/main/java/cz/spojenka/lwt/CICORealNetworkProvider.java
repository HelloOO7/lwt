package cz.spojenka.lwt;

import android.content.Context;
import android.util.Log;

import javax.net.ssl.SSLContext;

import cz.spojenka.lwdn.BluetoothLwdnScanner;
import cz.spojenka.lwdn.LwdnScanner;
import cz.spojenka.lwtp.LwtpTLSConfig;
import cz.spojenka.lwtp.LwtpTLSPolicy;

public class CICORealNetworkProvider implements ICICONetworkProvider {

    private final Context context;

    public CICORealNetworkProvider(Context context) {
        this.context = context;
    }

    @Override
    public LwdnScanner createLwdnScanner() {
        BluetoothLwdnScanner btScanner = LwtDeviceScanner.createBluetoothScanner(context);
        if (btScanner == null) {
            throw new UnsupportedOperationException("Bluetooth scanning is not supported on this device (use isSupported() to check before starting the service)");
        }
        return btScanner;
    }

    @Override
    public ICICODeviceClient createDeviceClient(LwtDevice device, SSLContext secureContext) {
        LwtAPIClient client = new LwtAPIClient(context, device.getAddress());
        if (secureContext != null) {
            client.useTLS(
                    new LwtpTLSConfig.Builder(device.getAddress())
                            .setTLSPolicy(LwtpTLSPolicy.EXPLICIT_REQUIRED)
                            .setSSLContext(secureContext)
                            .build()
            );
        }
        return new CICORealDeviceClient(client);
    }

    @Override
    public void close() {

    }
}
