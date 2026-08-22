package cz.spojenka.lwt.util;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import cz.spojenka.lwt.LwtCall;
import cz.spojenka.lwt.LwtClient;

public class RTTExecutionObserver implements LwtClient.ExecutionObserver {

    private final LwtCall<?> call;

    private Instant startTime;

    private Instant requestSentTime;
    private Instant responseReceivedTime;

    public RTTExecutionObserver(LwtCall<?> call) {
        this.call = call;
    }

    @Override
    public void onStartRequest(LwtCall<?> future) {
        if (future == call) {
            startTime = Instant.now();
        }
    }

    @Override
    public void onRequestSent(LwtCall<?> future) {
        if (future == call) {
            requestSentTime = Instant.now();
        }
    }

    @Override
    public void onResponseReceived(LwtCall<?> future) {
        if (future == call) {
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
