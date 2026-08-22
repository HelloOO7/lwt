package cz.spojenka.lwt;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

class MappingLwtCall<T, M> implements LwtCall<M> {

    private final LwtCall<T> originalCall;
    private final Function<T, M> mapper;

    public MappingLwtCall(LwtCall<T> originalCall, Function<T, M> mapper) {
        this.originalCall = originalCall;
        this.mapper = mapper;
    }

    @Override
    public CompletableFuture<M> enqueue(LwtSession session) {
        return originalCall.enqueue(session).thenApply(mapper);
    }

    @Override
    public M execute() throws IOException {
        return mapper.apply(originalCall.execute());
    }

    @Override
    public CompletableFuture<M> executeAsync(Executor executor) {
        return originalCall.executeAsync(executor).thenApply(mapper);
    }

    @Override
    public void cancel() {
        originalCall.cancel();
    }

    @Override
    public <M1> LwtCall<M1> map(Function<M, M1> mapper) {
        return new MappingLwtCall<>(this, mapper);
    }

    @Override
    public void onFinished(Runnable action) {
        originalCall.onFinished(action);
    }
}
