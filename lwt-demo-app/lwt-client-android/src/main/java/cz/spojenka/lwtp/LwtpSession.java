package cz.spojenka.lwtp;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

import cz.spojenka.lwdn.LwdnSocket;
import cz.spojenka.lwdn.LwdnSocketFactory;
import cz.spojenka.lwdn.SocketWatchdog;

public class LwtpSession {

    private final List<PendingRequest> pendingRequests = new ArrayList<>();
    private Duration watchdogTimeout = null;
    private final List<ExecutionObserver> observers = new ArrayList<>();

    public LwtpSession cloneAsEmpty() {
        LwtpSession newSession = new LwtpSession();
        cloneAsEmpty(newSession);
        return newSession;
    }

    protected void cloneAsEmpty(LwtpSession dest) {
        dest.watchdogTimeout = this.watchdogTimeout;
        dest.observers.addAll(this.observers);
    }

    public void addObserver(ExecutionObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void removeObserver(ExecutionObserver observer) {
        observers.remove(observer);
    }

    private void invokeObservers(Consumer<ExecutionObserver> action) {
        for (ExecutionObserver observer : observers) {
            action.accept(observer);
        }
    }

    protected LwtpPacket sendRequest(LwdnSocket socket, LwtpPacket request) throws IOException {
        invokeObservers(o -> o.onStartRequest(request));

        SocketWatchdog watchdog = null;
        if (watchdogTimeout != null) {
            watchdog = new SocketWatchdog(socket, watchdogTimeout);
            watchdog.start();
        }

        request.write(socket.getOutputStream());
        if (watchdog != null) {
            watchdog.resetWatchdog();
        }
        invokeObservers(o -> o.onRequestSent(request));

        invokeObservers(o -> o.onStartResponse(request));
        LwtpPacket response = new LwtpPacket(socket.getInputStream());
        if (watchdog != null) {
            watchdog.stopWatchdog();
        }
        invokeObservers(o -> o.onResponseReceived(request, response));

        return response;
    }

    /**
     * Set a timeout for a watchdog that will force close a socket if it does not respond for too long.
     * This is a workaround for the fact that the NimBLE server library is extremely broken and has race
     * conditions that prevent us from sending a disconnect signal by the server in case of an error.
     * Therefore, we can establish a timeout for the client to detect unresponsive connections and close them, which will trigger
     * a read/write error and return from blocking methods.
     *
     * @param watchdogTimeout the timeout duration for the watchdog, or null to disable the watchdog
     */
    public void setWatchdogTimeout(Duration watchdogTimeout) {
        this.watchdogTimeout = watchdogTimeout;
    }

    public Duration getWatchdogTimeout() {
        return watchdogTimeout;
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

    public void clear() {
        for (PendingRequest pendingRequest : pendingRequests) {
            pendingRequest.future.cancel(true);
        }
        pendingRequests.clear();
    }

    /**
     * Execute all pending requests in a single transaction. The requests will be sent in the order
     * they were added. After execution, the session will be cleared and can be reused for another transaction.
     *
     * @param socket the socket to use for the transaction
     */
    public void execute(LwdnSocket socket) {
        execute(socket, null);
    }

    public CompletableFuture<Void> executeAsync(LwdnSocket socket) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            try {
                execute(socket, future);
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    protected void execute(LwdnSocket socket, CompletableFuture<?> cancellationToken) {
        for (PendingRequest pendingRequest : pendingRequests) {
            if (cancellationToken != null && cancellationToken.isCancelled() && !pendingRequest.future.isCancelled()) {
                pendingRequest.future.cancel(true);
            }
            if (pendingRequest.future.isCancelled()) {
                continue;
            }
            try {
                LwtpPacket response = sendRequest(socket, pendingRequest.request);
                if (cancellationToken != null && cancellationToken.isCancelled()) {
                    pendingRequest.future.cancel(true);
                } else {
                    pendingRequest.future.complete(response);
                }
            } catch (Exception e) {
                if (cancellationToken != null && cancellationToken.isCancelled()) {
                    pendingRequest.future.cancel(true);
                } else {
                    pendingRequest.future.completeExceptionally(e);
                    if (cancellationToken != null && !cancellationToken.isCompletedExceptionally()) {
                        // only the first error will be reported to the overarching future, if present
                        cancellationToken.completeExceptionally(e);
                    }
                }
            }
        }
        pendingRequests.clear();
    }

    protected void finishRemainingWithException(Exception ex) {
        for (PendingRequest pendingRequest : pendingRequests) {
            if (!pendingRequest.future.isDone() && !pendingRequest.future.isCancelled()) {
                pendingRequest.future.completeExceptionally(ex);
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
        execute(socketFactory, null);
    }

    public CompletableFuture<Void> executeAsync(LwdnSocketFactory socketFactory) {
        return executeAsync(socketFactory, null);
    }

    public CompletableFuture<Void> executeAsync(LwdnSocketFactory socketFactory, Executor executor) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            try {
                execute(socketFactory, future);
                future.complete(null);
            } catch (Exception e) { // catch exceptions outside of the main calls
                future.completeExceptionally(e);
            }
        }, executor != null ? executor : ForkJoinPool.commonPool());
        return future;
    }

    private void execute(LwdnSocketFactory socketFactory, CompletableFuture<?> cancellationToken) {
        try (LwdnSocket socket = socketFactory.openSocket()) {
            execute(socket, cancellationToken);
        } catch (IOException ex) {
            // exception when opening socket
            finishRemainingWithException(ex);
            if (cancellationToken != null) {
                cancellationToken.completeExceptionally(ex);
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

    public static interface ExecutionObserver {

        public default void onStartRequest(LwtpPacket request) {

        }

        public default void onRequestSent(LwtpPacket request) {

        }

        public default void onStartResponse(LwtpPacket request) {

        }

        public default void onResponseReceived(LwtpPacket request, LwtpPacket response) {

        }
    }
}
