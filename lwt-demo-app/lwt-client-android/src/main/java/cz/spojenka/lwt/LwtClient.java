package cz.spojenka.lwt;

import android.content.Context;

import java.io.Closeable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

import cz.spojenka.lwdn.LwdnAddress;
import cz.spojenka.lwdn.LwdnSocketFactory;
import cz.spojenka.lwdn.TLSLwdnSocketFactory;
import cz.spojenka.lwtp.LwtpPacket;
import cz.spojenka.lwtp.LwtpSession;
import cz.spojenka.lwtp.LwtpTLSConfig;
import cz.spojenka.lwtp.LwtpTLSPolicy;
import cz.spojenka.lwtp.TLSLwtpSession;

public class LwtClient implements Closeable {

    private static final Object[] EMPTY_ARGS = new Object[]{ByteBuffer.allocate(0)};

    private final LwdnAddress address;
    private final LwdnSocketFactory baseSocketFactory;

    private LwtpSession baseSession = new LwtpSession();
    private LwdnSocketFactory socketFactory;

    private final WeakHashMap<LwtpPacket, LwtCall<?>> packetToResultMap = new WeakHashMap<>();

    private final List<ExecutionObserver> observers = new ArrayList<>();

    private final Set<CompletableFuture<?>> runningAsyncSessions = new HashSet<>();

    public LwtClient(Context context, LwdnAddress address) {
        this.address = address;
        this.baseSocketFactory = LwdnSocketFactory.create(context, address);
        this.socketFactory = baseSocketFactory;
    }

    @Override
    public void close() {
        if (runningAsyncSessions.isEmpty()) {
            baseSocketFactory.close();
        } else {
            // asynchronously finish closing when sessions are done
            CompletableFuture.allOf(runningAsyncSessions.toArray(new CompletableFuture[0])).whenComplete((result, ex) -> {
                baseSocketFactory.close();
            });
        }
    }

    public LwdnAddress getPeerAddress() {
        return address;
    }

    /**
     * @param timeout timeout
     * @see LwtpSession#setWatchdogTimeout(Duration)
     */
    public void setSocketWatchdogTimeout(Duration timeout) {
        baseSession.setWatchdogTimeout(timeout);
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

    public synchronized void addSessionExecutionObserver(LwtpSession.ExecutionObserver observer) {
        baseSession.addObserver(observer);
    }

    public synchronized void removeSessionExecutionObserver(LwtpSession.ExecutionObserver observer) {
        baseSession.removeObserver(observer);
    }

    private void changeSession(LwtpSession newSession) {
        Duration wdTimeout = baseSession.getWatchdogTimeout();
        baseSession = newSession;
        baseSession.setWatchdogTimeout(wdTimeout);
        baseSession.addObserver(new LwtpSession.ExecutionObserver() {
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

    private synchronized void invokeObservers(LwtpPacket key, BiConsumer<ExecutionObserver, LwtCall<?>> action) {
        LwtCall<?> call;
        synchronized (packetToResultMap) {
            call = packetToResultMap.get(key);
        }
        if (call != null) {
            for (ExecutionObserver observer : observers) {
                action.accept(observer, call);
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
                        return newCall(opAnnot.value(), bb, getResponseType(method));
                    } else {
                        throw new IllegalArgumentException("Method " + method + " must have exactly one non-null argument of type ByteBuffer (returned from FlatBufferBuilder)");
                    }
                }
            }
        });
    }

    private <T> LwtCallImpl<T> newCall(int operationId, ByteBuffer requestPacket, Class<T> responseType) {
        return new LwtCallImpl<T>(this, operationId, requestPacket, responseType);
    }

    public LwtSession newSession() {
        return new LwtSession(this, baseSession.cloneAsEmpty());
    }

    void execute(LwtpSession session) {
        CompletableFuture<Void> dummyFuture = new CompletableFuture<>();
        trackAsyncExecution(dummyFuture);
        try {
            session.execute(socketFactory);
        } finally {
            dummyFuture.complete(null);
        }
    }

    CompletableFuture<Void> executeAsync(LwtpSession session, Executor executor) {
        CompletableFuture<Void> future = session.executeAsync(socketFactory, executor);
        trackAsyncExecution(future);
        return future;
    }

    void registerPendingRequest(LwtpPacket request, LwtCall<?> result) {
        synchronized (packetToResultMap) {
            packetToResultMap.put(request, result);
        }
    }

    void trackAsyncExecution(CompletableFuture<?> execution) {
        synchronized (runningAsyncSessions) {
            runningAsyncSessions.add(execution);
            execution.whenComplete((result, ex) -> {
                synchronized (runningAsyncSessions) {
                    runningAsyncSessions.remove(execution);
                }
            });
        }
    }

    private Class<?> getResponseType(Method method) {
        Type returnType = method.getGenericReturnType();
        if (returnType instanceof ParameterizedType pt) {
            Type rawType = pt.getRawType();
            if (rawType == LwtCall.class) {
                Type[] typeArgs = pt.getActualTypeArguments();
                if (typeArgs.length == 1 && typeArgs[0] instanceof Class<?> clazz) {
                    return clazz;
                }
            }
        }
        throw new IllegalArgumentException("Method " + method + " must return LwtCall<T> for some T");
    }

    public static interface ExecutionObserver {

        public default void onStartRequest(LwtCall<?> future) {

        }

        public default void onRequestSent(LwtCall<?> future) {

        }

        public default void onStartResponse(LwtCall<?> future) {

        }

        public default void onResponseReceived(LwtCall<?> future) {

        }
    }
}
