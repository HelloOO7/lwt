package cz.spojenka.lwt;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

import cz.spojenka.lwtp.LwtpPacket;
import cz.spojenka.lwtp.LwtpSession;

public class LwtSession {

    private final LwtClient client;
    private final LwtpSession lwtpSession;

    private final WeakHashMap<LwtpPacket, LwtCall<?>> packetToResultMap = new WeakHashMap<>();

    private final List<LwtClient.ExecutionObserver> observers = new ArrayList<>();

    LwtSession(LwtClient client, LwtpSession lwtpSession) {
        this.client = client;
        this.lwtpSession = lwtpSession;
        lwtpSession.addObserver(new LwtpSession.ExecutionObserver() {
            @Override
            public void onStartRequest(LwtpPacket request) {
                invokeObservers(request, LwtClient.ExecutionObserver::onStartRequest);
            }

            @Override
            public void onRequestSent(LwtpPacket request) {
                invokeObservers(request, LwtClient.ExecutionObserver::onRequestSent);
            }

            @Override
            public void onStartResponse(LwtpPacket request) {
                invokeObservers(request, LwtClient.ExecutionObserver::onStartResponse);
            }

            @Override
            public void onResponseReceived(LwtpPacket request, LwtpPacket response) {
                invokeObservers(request, LwtClient.ExecutionObserver::onResponseReceived);
            }
        });
    }

    CompletableFuture<LwtpPacket> add(LwtpPacket packet) {
        return lwtpSession.add(packet);
    }

    public void execute() {
        client.execute(lwtpSession);
    }

    public CompletableFuture<Void> executeAsync(Executor executor) {
        return client.executeAsync(lwtpSession, executor);
    }

    public CompletableFuture<Void> executeAsync() {
        return executeAsync(null);
    }

    public void addExecutionObserver(LwtClient.ExecutionObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void removeExecutionObserver(LwtClient.ExecutionObserver observer) {
        observers.remove(observer);
    }

    public void addLwtpExecutionObserver(LwtpSession.ExecutionObserver observer) {
        lwtpSession.addObserver(observer);
    }

    public void removeLwtpExecutionObserver(LwtpSession.ExecutionObserver observer) {
        lwtpSession.removeObserver(observer);
    }

    private synchronized void invokeObservers(LwtpPacket key, BiConsumer<LwtClient.ExecutionObserver, LwtCall<?>> action) {
        LwtCall<?> call;
        synchronized (packetToResultMap) {
            call = packetToResultMap.get(key);
        }
        if (call != null) {
            for (LwtClient.ExecutionObserver observer : observers) {
                action.accept(observer, call);
            }
        }
    }

    void registerPendingRequest(LwtpPacket request, LwtCall<?> result) {
        synchronized (packetToResultMap) {
            packetToResultMap.put(request, result);
        }
    }
}
