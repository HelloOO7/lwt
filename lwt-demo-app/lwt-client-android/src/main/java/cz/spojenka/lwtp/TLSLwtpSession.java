package cz.spojenka.lwtp;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import cz.spojenka.lwdn.LwdnSocket;
import cz.spojenka.lwdn.TLSLwdnSocket;
import tlschannel.impl.TlsChannelImpl;

public class TLSLwtpSession extends LwtpSession {

    private final LwtpTLSConfig config;

    public TLSLwtpSession(LwtpTLSConfig config) {
        this.config = config;
    }

    @Override
    public TLSLwtpSession cloneAsEmpty() {
        TLSLwtpSession newSession = new TLSLwtpSession(config);
        cloneAsEmpty(newSession);
        return newSession;
    }

    protected void cloneAsEmpty(TLSLwtpSession dest) {
        super.cloneAsEmpty(dest);
    }

    private int startTLS(LwdnSocket socket) throws IOException {
        LwtpPacket response = sendRequest(socket, LwtpPacket.createSimpleControlMessage(LwtpControlCommand.START_TLS));
        return LwtpPacket.decodeSimpleControlCommand(response);
    }

    private boolean applyExplicitTLSPolicy(LwdnSocket socket) throws IOException {
        if (config.getTlsPolicy() == LwtpTLSPolicy.IMPLICIT) {
            throw new IllegalStateException("Caller must open a TLS socket on their own");
        }
        if (config.getTlsPolicy() == LwtpTLSPolicy.UNSECURED) {
            return false;
        }
        int tlsResult = startTLS(socket);
        if (tlsResult != LwtpControlCommand.GO_AHEAD) {
            if (config.getTlsPolicy() == LwtpTLSPolicy.EXPLICIT_REQUIRED) {
                throw new IOException("Server does not support START_TLS");
            }
            return false;
        } else {
            return true;
        }
    }

    /**
     * Transform a regular socket into a TLS socket using the configured SSL context.
     * This method should be used by callers which want to use implicit TLS, but also
     * run multiple sessions without performing a new handshake every time. If a {@link TLSLwdnSocket}
     * is passed into {@link #execute(LwdnSocket)}, it will be used directly without
     * attempting to open a TLS transport over it.
     *
     * @param socket the underlying socket to use for the TLS connection
     * @return a TLS socket
     */
    public TLSLwdnSocket createTLSSocket(LwdnSocket socket) {
        return TLSLwdnSocket.client(socket, config.getSslContext(), config.getPeerAddress());
    }

    @Override
    protected void execute(LwdnSocket socket, CompletableFuture<?> cancellationToken) {
        try {
            if (config.getTlsPolicy() == LwtpTLSPolicy.IMPLICIT) {
                if (socket instanceof TLSLwdnSocket) {
                    super.execute(socket, cancellationToken);
                } else {
                    // establish our own implicit connection if caller did not pass
                    // a TLS socket. this allows the caller to manage the TLS session themselves
                    // if they want to, but also allows us to transparently add TLS support if they do not.
                    TLSLwdnSocket tlsSocket = createTLSSocket(socket);
                    super.execute(tlsSocket, cancellationToken);
                    // shutdown TLS session (close notify). we do not close the
                    // underlying socket to maintain interface consistency.
                    tlsSocket.shutdown();
                }
            } else {
                // this will attempt TLS connection including START_TLS command.
                if (applyExplicitTLSPolicy(socket)) {
                    TLSLwdnSocket tlsSocket = createTLSSocket(socket);
                    super.execute(tlsSocket, cancellationToken);
                    // again, we will explicitly send a close-notify, as the calling function
                    // does not have a reference to the TLS socket, and if we let it call the close
                    // function, it would simply close the data path without sending a close
                    // notification
                    tlsSocket.shutdown();
                } else {
                    super.execute(socket);
                }
            }
        } catch (IOException ex) {
            finishRemainingWithException(ex, cancellationToken);
        }
    }
}
