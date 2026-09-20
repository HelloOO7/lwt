package cz.spojenka.android.system.livedata;

import android.util.Log;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;
import cz.spojenka.android.util.AsyncUtils;

public class LiveErrorSignal extends AdapterLiveData<Throwable> {

    public LiveErrorSignal() {
        super(null);
    }

    @Override
    protected Observer<? super Throwable> adaptObserver(Observer<? super Throwable> observer) {
        return err -> {
            if (err != null || observer instanceof ObserverWithAck) {
                observer.onChanged(err);
            }
        };
    }

    public void observe(LifecycleOwner lifecycleOwner, Observer<? super Throwable> observer, boolean notifyAcked) {
        if (notifyAcked) {
            observe(lifecycleOwner, new ObserverWithAck(observer));
        } else {
            observe(lifecycleOwner, observer);
        }
    }

    public boolean hasError() {
        return getValue() != null;
    }

    public void ack() {
        setValue(null);
    }

    /**
     * Handle the error that may happen when executing a future.
     *
     * @param future    the future
     * @param onSuccess callback to execute on success. if it throws, the error is caught here.
     * @param executor  executor to execute the callback on
     * @param <T>       type of the future result
     * @return A special future which produces the same result or error as the input future, but
     * does nothing on cancellation.
     */
    public <T> CompletableFuture<T> catchError(CompletableFuture<T> future, Consumer<T> onSuccess, Executor executor) {
        CompletableFuture<T> futureWithSilentCancel = new CompletableFuture<>();
        future.whenCompleteAsync((t, throwable) -> {
            throwable = AsyncUtils.unwrapCompletionException(throwable);
            if (throwable instanceof CancellationException) {
                return;
            }
            if (throwable != null) {
                logFailAndSetValue(onSuccess, throwable);
            } else {
                try {
                    onSuccess.accept(t);
                } catch (Throwable ex) {
                    logFailAndSetValue(onSuccess, ex);
                }
            }
        }, executor).whenComplete((t, throwable) -> {
            if (throwable != null) {
                if (!(throwable instanceof CancellationException)) {
                    futureWithSilentCancel.completeExceptionally(throwable);
                }
            } else {
                futureWithSilentCancel.complete(t);
            }
        });
        return futureWithSilentCancel;
    }

    private void logFailAndSetValue(Consumer<?> onSuccess, Throwable throwable) {
        String logTag;
        if (onSuccess.getClass().getEnclosingClass() != null) {
            logTag = onSuccess.getClass().getEnclosingClass().getSimpleName();
        } else if (!onSuccess.getClass().isAnonymousClass()) {
            logTag = onSuccess.getClass().getSimpleName();
        } else {
            logTag = "ErrorSignal";
        }
        Log.e(logTag, "Error in future", throwable);
        super.setValue(throwable);
    }

    @Override
    public void setValue(Throwable value) {
        if (value != null) {
            Log.e("ErrorSignal", "Set externally", value);
        }
        super.setValue(value);
    }

    public void handle(LifecycleOwner owner, ErrorHandler onError) {
        observe(owner, err -> onError.handle(err, this::ack));
    }

    public void handle(LifecycleOwner owner, Consumer<Throwable> onError) {
        handle(owner, (err, ack) -> {
            onError.accept(err);
            ack.run();
        });
    }

    @FunctionalInterface
    public static interface ErrorHandler {

        public void handle(Throwable error, Runnable ack);
    }

    private static class ObserverWithAck implements Observer<Throwable> {

        private final Observer<? super Throwable> base;

        public ObserverWithAck(Observer<? super Throwable> base) {
            this.base = base;
        }

        @Override
        public void onChanged(Throwable throwable) {
            base.onChanged(throwable);
        }
    }
}
