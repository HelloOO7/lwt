package cz.spojenka.lwt;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import cz.spojenka.lwtp.LwtpPacket;
import cz.spojenka.lwtp.LwtpSession;

public class LwtSession {

    private final LwtClient client;
    private final LwtpSession lwtpSession;

    LwtSession(LwtClient client, LwtpSession lwtpSession) {
        this.client = client;
        this.lwtpSession = lwtpSession;
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
}
