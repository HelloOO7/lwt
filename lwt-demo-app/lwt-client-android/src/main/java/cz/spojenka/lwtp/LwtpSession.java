package cz.spojenka.lwtp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import cz.spojenka.lwdn.LwdnSocket;
import cz.spojenka.lwdn.LwdnSocketFactory;

public class LwtpSession {

    private final List<PendingRequest> pendingRequests = new ArrayList<>();

    private LwtpPacket sendRequest(LwdnSocket socket, LwtpPacket request) throws IOException {
        request.write(socket.getOutputStream());
        return new LwtpPacket(socket.getInputStream());
    }

    /**
     * Add a pending request to the session. The request will not be sent until
     * {@link #execute(LwdnSocket)} is called. The returned future will be completed with the response
     * once the transaction is executed.
     *
     * @param request the request to add
     * @return the result future
     */
    public CompletableFuture<LwtpPacket> add(LwtpPacket request) {
        PendingRequest pendingRequest = new PendingRequest(request);
        pendingRequests.add(pendingRequest);
        return pendingRequest.future;
    }

    /**
     * Execute all pending requests in a single transaction. The requests will be sent in the order
     * they were added. After execution, the session will be cleared and can be reused for another transaction.
     *
     * @param socket the socket to use for the transaction
     */
    public void execute(LwdnSocket socket) {
        for (PendingRequest pendingRequest : pendingRequests) {
            try {
                LwtpPacket response = sendRequest(socket, pendingRequest.request);
                pendingRequest.future.complete(response);
            } catch (IOException e) {
                pendingRequest.future.completeExceptionally(e);
            }
        }
        pendingRequests.clear();
    }

    /**
     * Convenience method to obtain a socket, execute the session transaction and close
     * the socket immediately after.
     *
     * @param socketFactory the socket factory to obtain the socket from
     */
    public void execute(LwdnSocketFactory socketFactory) {
        try (LwdnSocket socket = socketFactory.openSocket()) {
            execute(socket);
        } catch (IOException ex) {
            // socket exception
            for (PendingRequest pendingRequest : pendingRequests) {
                if (!pendingRequest.future.isDone() && !pendingRequest.future.isCancelled()) {
                    pendingRequest.future.completeExceptionally(ex);
                }
            }
        }
    }

    /**
     * Separate each pending request into its own session. This may be needed for unstable
     * connections where retries are necessary.
     *
     * @return a list of sessions, each containing exactly one pending request
     */
    public List<LwtpSession> separate() {
        List<LwtpSession> sessions = new ArrayList<>();
        for (PendingRequest pendingRequest : pendingRequests) {
            LwtpSession session = new LwtpSession();
            session.pendingRequests.add(pendingRequest);
            sessions.add(session);
        }
        pendingRequests.clear();
        return sessions;
    }

    private static class PendingRequest {

        private final LwtpPacket request;
        private final CompletableFuture<LwtpPacket> future;

        public PendingRequest(LwtpPacket request) {
            this.request = request;
            this.future = new CompletableFuture<>();
        }
    }
}
