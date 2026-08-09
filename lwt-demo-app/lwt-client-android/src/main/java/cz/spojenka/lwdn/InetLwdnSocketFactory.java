package cz.spojenka.lwdn;

import java.io.IOException;
import java.net.InetAddress;

import javax.net.SocketFactory;

public class InetLwdnSocketFactory implements LwdnSocketFactory {

    private final SocketFactory socketFactory;
    private final InetAddress address;
    private final int port;

    public InetLwdnSocketFactory(SocketFactory socketFactory, InetAddress address, int port) {
        this.socketFactory = socketFactory;
        this.address = address;
        this.port = port;
    }

    @Override
    public InetLwdnSocket openSocket() throws IOException {
        return new InetLwdnSocket(socketFactory.createSocket(address, port));
    }

    @Override
    public void close() {

    }
}
