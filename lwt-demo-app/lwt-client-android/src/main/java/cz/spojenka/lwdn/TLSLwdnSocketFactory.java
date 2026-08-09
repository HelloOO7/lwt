package cz.spojenka.lwdn;

import java.io.IOException;

import javax.net.ssl.SSLContext;

public class TLSLwdnSocketFactory implements LwdnSocketFactory {

    private final LwdnSocketFactory baseFactory;
    private final SSLContext sslContext;
    private final LwdnAddress peerAddress;

    public TLSLwdnSocketFactory(LwdnSocketFactory baseFactory, SSLContext sslContext, LwdnAddress peerAddress) {
        this.baseFactory = baseFactory;
        this.sslContext = sslContext;
        this.peerAddress = peerAddress;
    }

    @Override
    public LwdnSocket openSocket() throws IOException {
        return TLSLwdnSocket.client(baseFactory.openSocket(), sslContext, peerAddress);
    }

    @Override
    public void close() {
        baseFactory.close();
    }
}
