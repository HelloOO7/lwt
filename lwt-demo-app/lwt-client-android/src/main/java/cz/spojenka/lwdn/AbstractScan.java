package cz.spojenka.lwdn;

import java.util.ArrayList;
import java.util.List;

public class AbstractScan<R, E extends Exception, THIS extends AbstractScan<R, E, THIS>> {

    private final List<R> results = new ArrayList<>();
    private boolean isFinished = false;

    private final List<OnResultListener<THIS, R, E>> resultListeners = new ArrayList<>();
    private final List<OnFinishedListener<THIS, R, E>> finishedListeners = new ArrayList<>();

    protected void addOnResultListenerImpl(OnResultListener<THIS, R, E> listener) {
        if (!resultListeners.contains(listener)) {
            resultListeners.add(listener);
        }
    }

    protected void addOnFinishedListenerImpl(OnFinishedListener<THIS, R, E> listener) {
        if (!finishedListeners.contains(listener)) {
            finishedListeners.add(listener);
        }
    }

    protected void removeOnResultListenerImpl(OnResultListener<THIS, R, E> listener) {
        resultListeners.remove(listener);
    }

    protected void removeOnFinishedListenerImpl(OnFinishedListener<THIS, R, E> listener) {
        finishedListeners.remove(listener);
    }

    @SuppressWarnings("unchecked")
    private THIS getThis() {
        return (THIS) this;
    }

    protected synchronized void addResult(R result) {
        results.add(result);
        for (var listener : resultListeners) {
            listener.onResult(getThis(), result);
        }
    }

    protected void markFinished() {
        isFinished = true;
        for (var listener : finishedListeners) {
            listener.onFinished(getThis());
        }
    }

    protected void markFailed(E e) {
        markFinished();
        for (var listener : resultListeners) {
            listener.onFailure(getThis(), e);
        }
        for (var listener : finishedListeners) {
            listener.onFailure(getThis(), e);
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

    protected static interface OnResultListener<S extends AbstractScan<R, E, S>, R, E extends Exception> {

        void onResult(S scan, R result);
        void onFailure(S scan, E e);
    }

    protected static interface OnFinishedListener<S extends AbstractScan<R, E, S>, R, E extends Exception> {

        void onFinished(S scan);
        void onFailure(S scan, E e);
    }
}
