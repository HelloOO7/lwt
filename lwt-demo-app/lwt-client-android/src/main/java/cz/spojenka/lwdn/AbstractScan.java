package cz.spojenka.lwdn;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractScan<R extends IScanResult, E extends Exception, THIS extends AbstractScan<R, E, THIS>> {

    private final List<R> results = new ArrayList<>();
    private boolean isFinished = false;
    private boolean wasCancelled = false;
    private E failureException = null;

    private final List<OnResultListener<THIS, R, E>> resultListeners = new ArrayList<>();
    private final List<OnFinishedListener<THIS, R, E>> finishedListeners = new ArrayList<>();

    protected synchronized void addOnResultListenerImpl(OnResultListener<THIS, R, E> listener) {
        if (!resultListeners.contains(listener)) {
            resultListeners.add(listener);
            for (R result : results) {
                listener.onResult(getThis(), result);
            }
            if (failureException != null) {
                listener.onFailure(getThis(), failureException);
            }
        }
    }

    protected synchronized void addOnFinishedListenerImpl(OnFinishedListener<THIS, R, E> listener) {
        if (!finishedListeners.contains(listener)) {
            finishedListeners.add(listener);
            if (isFinished()) {
                if (failureException != null) {
                    listener.onFinishedExceptionally(getThis(), failureException);
                } else {
                    listener.onFinished(getThis());
                }
            }
        }
    }

    protected synchronized void removeOnResultListenerImpl(OnResultListener<THIS, R, E> listener) {
        resultListeners.remove(listener);
    }

    protected synchronized void removeOnFinishedListenerImpl(OnFinishedListener<THIS, R, E> listener) {
        finishedListeners.remove(listener);
    }

    @SuppressWarnings("unchecked")
    private THIS getThis() {
        return (THIS) this;
    }

    private int findExistingResultIndex(R result) {
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).addressEquals(result)) {
                return i;
            }
        }
        return -1;
    }

    protected synchronized void addResult(R result) {
        if (isFinished()) {
            // not accepting any more results
            return;
        }
        int existingIndex = findExistingResultIndex(result);
        if (existingIndex != -1) {
            results.set(existingIndex, result);
        } else {
            results.add(result);
        }
        for (var listener : resultListeners) {
            listener.onResult(getThis(), result);
        }
    }

    protected synchronized void removeResult(R result) {
        if (isFinished()) {
            // not accepting any more results
            return;
        }
        if (results.removeIf(r -> r.addressEquals(result))) {
            for (var listener : resultListeners) {
                listener.onResultLost(getThis(), result);
            }
        }
    }

    protected synchronized void markFinished() {
        if (!isFinished) {
            isFinished = true;
            for (var listener : finishedListeners) {
                listener.onFinished(getThis());
            }
        }
    }

    protected synchronized void markFailed(E e) {
        this.isFinished = true;
        this.failureException = e;
        for (var listener : resultListeners) {
            listener.onFailure(getThis(), e);
        }
        for (var listener : finishedListeners) {
            listener.onFinishedExceptionally(getThis(), e);
        }
    }

    public synchronized int getResultCount() {
        return results.size();
    }

    public synchronized List<R> getResults() {
        return List.copyOf(results);
    }

    public boolean isFinished() {
        return isFinished;
    }

    public boolean wasCancelled() {
        return wasCancelled;
    }

    public synchronized void cancel() {
        if (!isFinished) {
            wasCancelled = true;
            onCancel();
            markFinished();
        }
    }

    protected abstract void onCancel();

    protected static interface OnResultListener<S extends AbstractScan<R, E, S>, R extends IScanResult, E extends Exception> {

        void onResult(S scan, R result);

        default void onResultLost(S scan, R result) {
            // optional
        }

        void onFailure(S scan, E e);
    }

    protected static interface OnFinishedListener<S extends AbstractScan<R, E, S>, R extends IScanResult, E extends Exception> {

        void onFinished(S scan);

        void onFinishedExceptionally(S scan, E e);
    }
}
