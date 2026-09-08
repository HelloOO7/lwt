package cz.spojenka.lwt;

import com.google.flatbuffers.FlatBufferBuilder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;

import cz.spojenka.lwt.util.FlatbufferUtils;
import cz.spojenka.lwtp.LwtpPacket;

class LwtCallImpl<T> implements LwtCall<T> {

    private final LwtClient client;
    private final int operationId;
    private final ByteBuffer requestBuffer;
    private final Class<T> responseType;

    private CompletableFuture<LwtpPacket> lwtpFuture;
    private CompletableFuture<T> enqueuedFuture;
    private boolean isCancelled = false;

    private final List<LwtClient.ExecutionObserver> executionObservers = new ArrayList<>();
    private final List<Runnable> onFinishedCallbacks = new ArrayList<>();

    LwtCallImpl(LwtClient client, int operationId, ByteBuffer requestBuffer, Class<T> responseType) {
        this.client = client;
        this.operationId = operationId;
        this.requestBuffer = requestBuffer;
        this.responseType = responseType;
    }

    @Override
    public void cancel() {
        if (enqueuedFuture != null) {
            enqueuedFuture.cancel(true);
            lwtpFuture.cancel(true);
            isCancelled = true;
        }
    }

    @Override
    public <M> LwtCall<M> map(MappingFunction<T, M> mapper) {
        return new MappingLwtCall<>(this, mapper);
    }

    @Override
    public void onFinished(Runnable action) {
        onFinishedCallbacks.add(action);
    }

    @Override
    public void observeExecution(LwtClient.ExecutionObserver observer) {
        executionObservers.add(new LwtClient.ExecutionObserver() {
            @Override
            public void onStartRequest(LwtCall<?> future) {
                invokeObserverIfMatch(future, observer::onStartRequest);
            }

            @Override
            public void onRequestSent(LwtCall<?> future) {
                invokeObserverIfMatch(future, observer::onRequestSent);
            }

            @Override
            public void onStartResponse(LwtCall<?> future) {
                invokeObserverIfMatch(future, observer::onStartResponse);
            }

            @Override
            public void onResponseReceived(LwtCall<?> future) {
                invokeObserverIfMatch(future, observer::onResponseReceived);
            }

            private void invokeObserverIfMatch(LwtCall<?> future, Consumer<LwtCall<?>> consumer) {
                if (future == LwtCallImpl.this) {
                    consumer.accept(future);
                }
            }
        });
    }

    @Override
    public CompletableFuture<T> enqueue(LwtSession session) {
        if (isCancelled) {
            CompletableFuture<T> cancelledFuture = new CompletableFuture<>();
            cancelledFuture.cancel(true);
            return cancelledFuture;
        }
        executionObservers.forEach(session::addExecutionObserver);
        LwtpPacket requestPacket = new LwtpPacket(createRequestFlatbuffer(operationId, requestBuffer));
        enqueuedFuture = createResponseFuture(lwtpFuture = session.add(requestPacket), responseType);
        enqueuedFuture.whenComplete((result, ex) -> {
            executionObservers.forEach(session::removeExecutionObserver);
            for (Runnable callback : onFinishedCallbacks) {
                callback.run();
            }
        });
        session.registerPendingRequest(requestPacket, this);
        return enqueuedFuture;
    }

    @Override
    public T execute() throws IOException {
        LwtSession session = client.newSession();
        enqueuedFuture = enqueue(session);
        session.execute();
        try {
            return enqueuedFuture.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof IOException ioe) {
                throw ioe;
            } else {
                throw e;
            }
        }
    }

    @Override
    public CompletableFuture<T> executeAsync(Executor executor) {
        LwtSession session = client.newSession();
        enqueuedFuture = enqueue(session);
        session.executeAsync(executor);
        return enqueuedFuture;
    }

    private static ByteBuffer createRequestFlatbuffer(int operationId, ByteBuffer data) {
        FlatBufferBuilder builder = new FlatBufferBuilder();
        int dataVec = builder.createByteVector(data);
        builder.finish(RequestPacket.createRequestPacket(builder, operationId, dataVec));
        return builder.dataBuffer();
    }

    private static ByteBuffer unwrapResponseFlatbuffer(ByteBuffer response) throws LwtStatusException {
        ResponsePacket respPacket = ResponsePacket.getRootAsResponsePacket(response);
        if (!LwtStatus.isOK(respPacket.statusCode())) {
            throw new LwtStatusException(respPacket.statusCode());
        }
        return respPacket.dataAsByteBuffer();
    }

    private static <T> CompletableFuture<T> createResponseFuture(CompletableFuture<LwtpPacket> baseFuture, Class<T> responseType) {
        CompletableFuture<T> responseFuture = new CompletableFuture<>();
        baseFuture.whenComplete((lwtpResp, ex) -> {
            if (ex != null) {
                responseFuture.completeExceptionally(ex);
            } else {
                try {
                    ByteBuffer respData = unwrapResponseFlatbuffer(lwtpResp.getPayload());
                    T responseObj = FlatbufferUtils.reflectOpenFlatbuffer(respData, responseType);
                    responseFuture.complete(responseObj);
                } catch (Throwable e) {
                    responseFuture.completeExceptionally(e);
                }
            }
        });
        return responseFuture;
    }
}
