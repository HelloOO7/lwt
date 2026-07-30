package cz.spojenka.lwt.util;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import cz.spojenka.lwt.LwtClient;

public class RTTExecutionObserver implements LwtClient.ExecutionObserver {

    private final CompletableFuture<?> requestFuture;

    private Instant startTime;

    private Instant requestSentTime;
    private Instant responseReceivedTime;

    public RTTExecutionObserver(CompletableFuture<?> requestFuture) {
        this.requestFuture = requestFuture;
    }

    @Override
    public void onStartRequest(CompletableFuture<?> future) {
        if (future == requestFuture) {
            startTime = Instant.now();
        }
    }

    @Override
    public void onRequestSent(CompletableFuture<?> future) {
        if (future == requestFuture) {
            requestSentTime = Instant.now();
        }
    }

    @Override
    public void onResponseReceived(CompletableFuture<?> future) {
        if (future == requestFuture) {
            responseReceivedTime = Instant.now();
        }
    }

    public Instant getRequestSentTime() {
        return requestSentTime;
    }

    public Instant getRoundTripStartTime() {
        return startTime;
    }

    public Duration getRoundTripDuration() {
        if (startTime != null && responseReceivedTime != null) {
            return Duration.between(startTime, responseReceivedTime);
        } else {
            throw new IllegalStateException("Request has not been sent or response has not been received yet.");
        }
    }
}
