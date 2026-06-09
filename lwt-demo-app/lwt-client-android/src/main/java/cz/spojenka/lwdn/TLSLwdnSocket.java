package cz.spojenka.lwdn;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import tlschannel.ClientTlsChannel;
import tlschannel.ServerTlsChannel;
import tlschannel.TlsChannel;

public class TLSLwdnSocket implements LwdnSocket {

    private final TlsChannel tlsChannel;

    private TLSLwdnSocket(TlsChannel tlsChannel) {
        this.tlsChannel = tlsChannel;
    }

    /**
     * Create a client TLS socket for a given LWDN device. The address must be provided so that
     * a hostname and port can be derived for certificate validation as well as session resumption.
     *
     * @param socket        the underlying socket to use for the TLS connection
     * @param sslContext    the SSL context to use for the TLS connection
     * @param serverAddress the address of the server to connect to, used for session resumption
     * @return a new TLS socket connected to the given server address. Handshake will be performed lazily when the input/output stream is first accessed.
     */
    public static TLSLwdnSocket client(LwdnSocket socket, SSLContext sslContext, LwdnAddress serverAddress) {
        SSLEngine engine = sslContext.createSSLEngine(serverAddress.getLocalHostName(), serverAddress.getPortNumber());
        engine.setUseClientMode(true);
        engine.getSSLParameters().setEndpointIdentificationAlgorithm("HTTPS");
        return new TLSLwdnSocket(ClientTlsChannel.newBuilder(new LwdnByteChannel(socket), engine).build());
    }

    /**
     * Create a server TLS socket. This is actually created over an existing connected socket,
     * not as a listening socket. This method is untested, as LWDN servers are not currently
     * implemented on Android.
     *
     * @param socket     the underlying socket
     * @param sslContext the SSL context to use for the TLS connection
     * @return a new TLS socket that will perform a server-side handshake when the input/output stream is first accessed
     */
    public static TLSLwdnSocket server(LwdnSocket socket, SSLContext sslContext) {
        return new TLSLwdnSocket(ServerTlsChannel.newBuilder(new LwdnByteChannel(socket), sslContext).build());
    }

    private InputStream cachedInputStream;

    @Override
    public InputStream getInputStream() throws IOException {
        if (cachedInputStream == null) {
            cachedInputStream = Channels.newInputStream(tlsChannel);
        }
        return cachedInputStream;
    }

    private OutputStream cachedOutputStream;

    @Override
    public OutputStream getOutputStream() throws IOException {
        if (cachedOutputStream == null) {
            cachedOutputStream = Channels.newOutputStream(tlsChannel);
        }
        return cachedOutputStream;
    }

    @Override
    public boolean isOpen() {
        return tlsChannel.isOpen();
    }

    @Override
    public void close() throws IOException {
        tlsChannel.close();
    }

    /**
     * Shut down the TLS session using close notify without closing the underlying socket.
     * See {@link TlsChannel#shutdown()} for details.
     *
     * @throws IOException if an I/O error occurs during shutdown
     */
    public void shutdown() throws IOException {
        if (!tlsChannel.shutdown()) {
            // finish bidirectional shutdown (wait for client ack)
            tlsChannel.shutdown();
        }
    }
}
