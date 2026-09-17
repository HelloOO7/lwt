package cz.spojenka.lwt;

import java.io.Closeable;

import javax.net.ssl.SSLContext;

import cz.spojenka.lwdn.LwdnScanner;

public interface ICICONetworkProvider extends Closeable {

    public LwdnScanner createLwdnScanner();
    public ICICODeviceClient createDeviceClient(LwtDevice device, SSLContext secureContext);
    @Override
    public void close();
}
