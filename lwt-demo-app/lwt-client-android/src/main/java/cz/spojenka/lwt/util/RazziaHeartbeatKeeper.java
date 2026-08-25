package cz.spojenka.lwt.util;

import android.os.Handler;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import androidx.lifecycle.MutableLiveData;
import cz.spojenka.lwt.LwtAPIClient;
import cz.spojenka.lwt.SetRazziaResponse;

public class RazziaHeartbeatKeeper implements AutoCloseable {

    private final Handler handler;
    private final Supplier<LwtAPIClient> clientSupplier;
    private final Executor callExecutor;

    private final Runnable heartbeatRunnable = () -> updateRazzia(true);

    private final MutableLiveData<State> stateLiveData = new MutableLiveData<>(new State(false, false, null));

    public RazziaHeartbeatKeeper(Handler handler, Supplier<LwtAPIClient> clientSupplier, Executor callExecutor) {
        this.handler = handler;
        this.clientSupplier = clientSupplier;
        this.callExecutor = callExecutor != null ? callExecutor : Executors.newSingleThreadExecutor();
    }

    public RazziaHeartbeatKeeper(Handler handler, Supplier<LwtAPIClient> clientSupplier) {
        this(handler, clientSupplier, null);
    }

    public RazziaHeartbeatKeeper(Handler handler, LwtAPIClient client, Executor callExecutor) {
        this(handler, () -> client, callExecutor);
    }

    public RazziaHeartbeatKeeper(Handler handler, LwtAPIClient client) {
        this(handler, () -> client, null);
    }

    @Override
    public void close() {
        handler.removeCallbacks(heartbeatRunnable);
    }

    public CompletableFuture<SetRazziaResponse> setRazzia(boolean enabled) {
        var current = stateLiveData.getValue();
        if (current != null && current.isRazzia() == enabled) {
            return CompletableFuture.completedFuture(null);
        }
        handler.removeCallbacks(heartbeatRunnable);
        stateLiveData.setValue(new State(enabled, true, null));
        return updateRazzia(enabled);
    }

    private CompletableFuture<SetRazziaResponse> updateRazzia(boolean enabled) {
        return clientSupplier.get().setRazzia(enabled).executeAsync(callExecutor).whenComplete((resp, throwable) -> {
            if (throwable == null) {
                if (enabled && resp.requestedHeartbeat() != 0) {
                    handler.postDelayed(heartbeatRunnable, Duration.ofSeconds(resp.requestedHeartbeat()).minusSeconds(5).toMillis());
                }
                stateLiveData.postValue(new State(enabled, false, null));
            } else {
                stateLiveData.postValue(new State(false, false, throwable));
            }
        });
    }

    public MutableLiveData<State> getStateLiveData() {
        return stateLiveData;
    }

    public void ackError() {
        State curState = Objects.requireNonNull(stateLiveData.getValue());
        if (curState.error() != null) {
            stateLiveData.setValue(new State(curState.isRazzia(), curState.isProcessing(), null));
        }
    }

    public static record State(boolean isRazzia, boolean isProcessing, Throwable error) {

    }
}
