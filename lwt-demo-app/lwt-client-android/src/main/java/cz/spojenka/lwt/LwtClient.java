package cz.spojenka.lwt;

import com.google.flatbuffers.FlatBufferBuilder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

import cz.spojenka.lwdn.LwdnSocketFactory;
import cz.spojenka.lwtp.LwtpPacket;
import cz.spojenka.lwtp.LwtpSession;

public class LwtClient {

    private static final Object[] EMPTY_ARGS = new Object[]{ByteBuffer.allocate(0)};

    private final LwtpSession lwtpSession = new LwtpSession();
    private final LwdnSocketFactory socketFactory;

    public LwtClient(LwdnSocketFactory socketFactory) {
        this.socketFactory = socketFactory;
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
                        return createResponseFuture(baseFuture, getResponseType(method));
                    } else {
                        throw new IllegalArgumentException("Method " + method + " must have exactly one non-null argument of type ByteBuffer (returned from FlatBufferBuilder)");
                    }
                }
            }
        });
    }

    public void execute() {
        lwtpSession.execute(socketFactory);
    }

    private <T> CompletableFuture<T> createResponseFuture(CompletableFuture<LwtpPacket> baseFuture, Class<T> responseType) {
        CompletableFuture<T> responseFuture = new CompletableFuture<>();
        baseFuture.whenComplete((lwtpResp, ex) -> {
            if (ex != null) {
                responseFuture.completeExceptionally(ex);
            } else {
                try {
                    ByteBuffer respData = unwrapResponseFlatbuffer(lwtpResp.getPayload());
                    T responseObj = reflectOpenFlatbuffer(respData, responseType);
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

    @SuppressWarnings("unchecked")
    private <T> T reflectOpenFlatbuffer(ByteBuffer data, Class<T> clazz) {
        try {
            Method getRootAsMethod = clazz.getMethod("getRootAs" + clazz.getSimpleName(), ByteBuffer.class);
            return (T) getRootAsMethod.invoke(null, data);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
