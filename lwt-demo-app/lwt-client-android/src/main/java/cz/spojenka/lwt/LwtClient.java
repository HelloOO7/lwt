package cz.spojenka.lwt;

import com.google.flatbuffers.FlatBufferBuilder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

import cz.spojenka.lwdn.LwdnAddress;
import cz.spojenka.lwdn.LwdnSocketFactory;
import cz.spojenka.lwdn.TLSLwdnSocketFactory;
import cz.spojenka.lwt.util.FlatbufferUtils;
import cz.spojenka.lwtp.LwtpPacket;
import cz.spojenka.lwtp.LwtpSession;
import cz.spojenka.lwtp.LwtpTLSConfig;
import cz.spojenka.lwtp.LwtpTLSPolicy;
import cz.spojenka.lwtp.TLSLwtpSession;

public class LwtClient {

    private static final Object[] EMPTY_ARGS = new Object[]{ByteBuffer.allocate(0)};

    private final LwdnAddress address;
    private final LwdnSocketFactory baseSocketFactory;

    private LwtpSession lwtpSession = new LwtpSession();
    private LwdnSocketFactory socketFactory;

    private WeakHashMap<LwtpPacket, CompletableFuture<?>> packetToResultMap = new WeakHashMap<>();

    private final List<ExecutionObserver> observers = new ArrayList<>();

    public LwtClient(LwdnAddress address) {
        this.address = address;
        this.baseSocketFactory = LwdnSocketFactory.create(address);
        this.socketFactory = baseSocketFactory;
    }

    public LwdnAddress getPeerAddress() {
        return address;
    }

    /**
     * @param timeout timeout
     * @see LwtpSession#setWatchdogTimeout(Duration)
     */
    public void setSocketWatchdogTimeout(Duration timeout) {
        lwtpSession.setWatchdogTimeout(timeout);
    }

    public void useTLS(LwtpTLSConfig tlsConfig) {
        if (tlsConfig == null || tlsConfig.getTlsPolicy() == LwtpTLSPolicy.UNSECURED) {
            socketFactory = baseSocketFactory;
            changeSession(new LwtpSession());
        } else {
            if (tlsConfig.getTlsPolicy() == LwtpTLSPolicy.IMPLICIT) {
                // wrap the socket factory with a TLS layer here.
                // we could delegate this to TLSLwtpSession, but it is better to do it here,
                // as it transfers control of close notification to us
                socketFactory = new TLSLwdnSocketFactory(baseSocketFactory, tlsConfig.getSslContext(), tlsConfig.getPeerAddress());
            } else {
                socketFactory = baseSocketFactory;
            }
            changeSession(new TLSLwtpSession(tlsConfig));
        }
    }

    public void disableTLS() {
        useTLS(null);
    }

    public synchronized void addExecutionObserver(ExecutionObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public synchronized void removeExecutionObserver(ExecutionObserver observer) {
        observers.remove(observer);
    }

    private void changeSession(LwtpSession newSession) {
        Duration wdTimeout = lwtpSession.getWatchdogTimeout();
        lwtpSession = newSession;
        lwtpSession.setWatchdogTimeout(wdTimeout);
        lwtpSession.addObserver(new LwtpSession.ExecutionObserver() {
            @Override
            public void onStartRequest(LwtpPacket request) {
                invokeObservers(request, ExecutionObserver::onStartRequest);
            }

            @Override
            public void onRequestSent(LwtpPacket request) {
                invokeObservers(request, ExecutionObserver::onRequestSent);
            }

            @Override
            public void onStartResponse(LwtpPacket request) {
                invokeObservers(request, ExecutionObserver::onStartResponse);
            }

            @Override
            public void onResponseReceived(LwtpPacket request, LwtpPacket response) {
                invokeObservers(request, ExecutionObserver::onResponseReceived);
            }
        });
    }

    private synchronized void invokeObservers(LwtpPacket key, BiConsumer<ExecutionObserver, CompletableFuture<?>> action) {
        CompletableFuture<?> future = packetToResultMap.get(key);
        if (future != null) {
            for (ExecutionObserver observer : observers) {
                action.accept(observer, future);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <I> I bind(Class<I> iface) {
        return (I) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (method.getDeclaringClass() == Object.class) {
                    return method.invoke(this, args);
                } else {
                    LwtOperation opAnnot = method.getAnnotation(LwtOperation.class);
                    if (opAnnot == null) {
                        throw new IllegalArgumentException("Method " + method + " is not annotated with @LwtOperation");
                    }
                    Object[] realArgs = args;
                    if (realArgs == null) {
                        realArgs = EMPTY_ARGS;
                    }
                    if (realArgs.length == 1 && realArgs[0] != null && realArgs[0] instanceof ByteBuffer bb) {
                        ByteBuffer req = createRequestFlatbuffer(opAnnot.value(), bb);
                        LwtpPacket lwtpReq = new LwtpPacket(req);
                        CompletableFuture<LwtpPacket> baseFuture = lwtpSession.add(lwtpReq);
                        CompletableFuture<?> responseFuture = createResponseFuture(baseFuture, getResponseType(method));
                        packetToResultMap.put(lwtpReq, responseFuture);
                        return responseFuture;
                    } else {
                        throw new IllegalArgumentException("Method " + method + " must have exactly one non-null argument of type ByteBuffer (returned from FlatBufferBuilder)");
                    }
                }
            }
        });
    }

    public void execute() {
        LwtpSession execSession = lwtpSession;
        lwtpSession = lwtpSession.cloneAsEmpty();
        execSession.execute(socketFactory);
    }

    /**
     * Execute all pending requests asynchronously. After this is called, the client
     * can be reused for configuring new sessions even while the previous session is
     * still being executed.
     *
     * @param executor the executor to run the execution on. If null, the default executor will be used.
     *
     * @return Future that will be completed when all requests finish,
     * and which can be used to cancel all execution.
     */
    public CompletableFuture<Void> executeAsync(Executor executor) {
        LwtpSession execSession = lwtpSession;
        lwtpSession = lwtpSession.cloneAsEmpty();
        return execSession.executeAsync(socketFactory, executor);
    }

    public CompletableFuture<Void> executeAsync() {
        return executeAsync(null);
    }

    private <T> CompletableFuture<T> createResponseFuture(CompletableFuture<LwtpPacket> baseFuture, Class<T> responseType) {
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

    private ByteBuffer createRequestFlatbuffer(int operationId, ByteBuffer data) {
        FlatBufferBuilder builder = new FlatBufferBuilder();
        int dataVec = builder.createByteVector(data);
        builder.finish(RequestPacket.createRequestPacket(builder, operationId, dataVec));
        return builder.dataBuffer();
    }

    private ByteBuffer unwrapResponseFlatbuffer(ByteBuffer response) throws LwtStatusException {
        ResponsePacket respPacket = ResponsePacket.getRootAsResponsePacket(response);
        if (!LwtStatus.isOK(respPacket.statusCode())) {
            throw new LwtStatusException(respPacket.statusCode());
        }
        return respPacket.dataAsByteBuffer();
    }

    private Class<?> getResponseType(Method method) {
        Type returnType = method.getGenericReturnType();
        if (returnType instanceof ParameterizedType pt) {
            Type rawType = pt.getRawType();
            if (rawType == CompletableFuture.class) {
                Type[] typeArgs = pt.getActualTypeArguments();
                if (typeArgs.length == 1 && typeArgs[0] instanceof Class<?> clazz) {
                    return clazz;
                }
            }
        }
        throw new IllegalArgumentException("Method " + method + " must return CompletableFuture<T> for some T");
    }

    public static interface ExecutionObserver {

        public default void onStartRequest(CompletableFuture<?> future) {

        }

        public default void onRequestSent(CompletableFuture<?> future) {

        }

        public default void onStartResponse(CompletableFuture<?> future) {

        }

        public default void onResponseReceived(CompletableFuture<?> future) {

        }
    }
}
