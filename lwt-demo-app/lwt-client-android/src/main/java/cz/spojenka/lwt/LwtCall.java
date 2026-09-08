package cz.spojenka.lwt;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public interface LwtCall<T> {

    /**
     * Enqueues an LWT operation that takes a ByteBuffer request. The operation will be run when the session
     * is executed.
     *
     * @param session the LWT session to enqueue the operation in
     * @return a future that will be completed with the result of the operation once execute() is called
     */
    public CompletableFuture<T> enqueue(LwtSession session);

    /**
     * Convenience method to enqueue and immediately execute an LWT operation. This is equivalent to calling
     * enqueue() followed by executing a session. A new LWTP session and LWDN socket will be opened and closed for this operation,
     * so it is not recommended to use this method for multiple operations in a row, as it will be less
     * efficient than enqueuing them together and executing once.
     *
     * @return the result of the operation
     * @throws IOException                                if there was an I/O error executing the operation
     * @throws java.util.concurrent.CompletionException   if something else during the operation failed
     * @throws java.util.concurrent.CancellationException if the operation was cancelled
     */
    public T execute() throws IOException;

    public CompletableFuture<T> executeAsync(Executor executor);

    public default CompletableFuture<T> executeAsync() {
        return executeAsync(null);
    }

    public void cancel();

    public <M> LwtCall<M> map(MappingFunction<T, M> mapper);

    public void onFinished(Runnable action);

    public void observeExecution(LwtClient.ExecutionObserver observer);

    @FunctionalInterface
    public static interface MappingFunction<T, M> {

        public M apply(T value) throws Exception;
    }
}
