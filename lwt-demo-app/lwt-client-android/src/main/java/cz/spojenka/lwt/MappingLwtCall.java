package cz.spojenka.lwt;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

class MappingLwtCall<T, M> implements LwtCall<M> {

    private final LwtCall<T> originalCall;
    private final MappingFunction<T, M> mapper;

    public MappingLwtCall(LwtCall<T> originalCall, MappingFunction<T, M> mapper) {
        this.originalCall = originalCall;
        this.mapper = mapper;
    }

    private CompletableFuture<M> applyMapper(CompletableFuture<T> future) {
        CompletableFuture<M> mappedFuture = new CompletableFuture<>();
        future.whenComplete((result, ex) -> {
            try {
                if (ex != null) {
                    mappedFuture.completeExceptionally(ex);
                } else {
                    try {
                        M mappedResult = mapper.apply(result);
                        mappedFuture.complete(mappedResult);
                    } catch (Exception e) {
                        mappedFuture.completeExceptionally(e);
                    }
                }
            } catch (Throwable e) {
                mappedFuture.completeExceptionally(e);
            }
        });
        return mappedFuture;
    }

    @Override
    public CompletableFuture<M> enqueue(LwtSession session) {
        return applyMapper(originalCall.enqueue(session));
    }

    @Override
    public M execute() throws IOException {
        try {
            return mapper.apply(originalCall.execute());
        } catch (IOException ioe) {
            throw ioe;
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }

    @Override
    public CompletableFuture<M> executeAsync(Executor executor) {
        return applyMapper(originalCall.executeAsync(executor));
    }

    @Override
    public void cancel() {
        originalCall.cancel();
    }

    @Override
    public <M1> LwtCall<M1> map(MappingFunction<M, M1> mapper) {
        return new MappingLwtCall<>(this, mapper);
    }

    @Override
    public void onFinished(Runnable action) {
        originalCall.onFinished(action);
    }

    @Override
    public void observeExecution(LwtClient.ExecutionObserver observer) {
        originalCall.observeExecution(observer);
    }
}
