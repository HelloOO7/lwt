package cz.spojenka.lwt;

import android.bluetooth.BluetoothDevice;
import android.util.Log;

import com.google.flatbuffers.FlatBufferBuilder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import cz.spojenka.lwdn.BluetoothLwdnAddress;
import cz.spojenka.lwdn.LwdnAddress;
import cz.spojenka.lwdn.LwdnSocketFactory;
import cz.spojenka.lwt.util.ByteBufferUtils;
import cz.spojenka.lwt.util.TLSTrustManager;

public class LwtAPIClient extends LwtClient {

    private static final String TAG = "LwtAPIClient";

    private static final int BLUETOOTH_PSM = LwtServiceConstants.BLE_API_PSM;

    private final LwtAPI api;
    private final SecureRandom random = new SecureRandom();

    public LwtAPIClient(LwdnSocketFactory socketFactory) {
        super(socketFactory);
        api = bind(LwtAPI.class);
    }

    public LwtAPIClient(LwdnAddress address) {
        super(address);
        api = bind(LwtAPI.class);
    }

    public static BluetoothLwdnAddress bluetoothAddress(BluetoothDevice device) {
        return new BluetoothLwdnAddress(device, BLUETOOTH_PSM);
    }

    /**
     * Enqueues an LWT operation for execution. The operation will be run when execute() is called.
     *
     * @param operation the operation, as a LwtAPI method reference, e.g. LwtAPI::ping
     * @param <T>       the type of the operation result
     * @return a future that will be completed with the result of the operation once execute() is called
     */
    public <T> CompletableFuture<T> enqueue(Function<LwtAPI, CompletableFuture<T>> operation) {
        return operation.apply(api);
    }

    /**
     * Convenience method to enqueue and immediately execute an LWT operation. This is equivalent to calling
     * enqueue() followed by execute(). A new LWTP session and LWDN socket will be opened and closed for this operation,
     * so it is not recommended to use this method for multiple operations in a row, as it will be less
     * efficient than enqueuing them together and executing once.
     *
     * @param operation the operation, as a LwtAPI method reference, e.g. LwtAPI::ping
     * @param <T>       the type of the operation result
     * @return a future that will be completed with the result of the operation
     */
    public <T> CompletableFuture<T> call(Function<LwtAPI, CompletableFuture<T>> operation) {
        CompletableFuture<T> future = enqueue(operation);
        execute();
        return future;
    }

    /**
     * Enqueues an LWT operation that takes a ByteBuffer request. The operation will be run when execute() is called.
     *
     * @param operation the operation, as a LwtAPI method reference, e.g. LwtAPI::getTicketValidationInfo
     * @param request   the ByteBuffer flatbuffer request to pass to the operation
     * @param <T>       the type of the operation result
     * @return a future that will be completed with the result of the operation once execute() is called
     */
    public <T> CompletableFuture<T> enqueue(BiFunction<LwtAPI, ByteBuffer, CompletableFuture<T>> operation, ByteBuffer request) {
        return operation.apply(api, request);
    }

    /**
     * Convenience method to enqueue and immediately execute an LWT operation that takes a ByteBuffer request. This is equivalent to calling
     * enqueue() followed by execute(). A new LWTP session and LWDN socket will be opened and closed for this operation,
     * so it is not recommended to use this method for multiple operations in a row, as it will be less
     * efficient than enqueuing them together and executing once.
     *
     * @param operation the operation, as a LwtAPI method reference, e.g. LwtAPI::getTicketValidationInfo
     * @param request   the ByteBuffer flatbuffer request to pass to the operation
     * @param <T>       the type of the operation result
     * @return a future that will be completed with the result of the operation
     */
    public <T> CompletableFuture<T> call(BiFunction<LwtAPI, ByteBuffer, CompletableFuture<T>> operation, ByteBuffer request) {
        CompletableFuture<T> future = enqueue(operation, request);
        execute();
        return future;
    }

    private <T> CompletableFuture<T> enqueueOrCall(Function<LwtAPI, CompletableFuture<T>> operation, CommType comm) {
        if (comm == CommType.ENQUEUE) {
            return enqueue(operation);
        } else {
            return call(operation);
        }
    }

    private <T> CompletableFuture<T> enqueueOrCall(BiFunction<LwtAPI, ByteBuffer, CompletableFuture<T>> operation, ByteBuffer request, CommType comm) {
        if (comm == CommType.ENQUEUE) {
            return enqueue(operation, request);
        } else {
            return call(operation, request);
        }
    }

    public CompletableFuture<PingResponse> ping(CommType comm) {
        return enqueueOrCall(LwtAPI::ping, comm);
    }

    public CompletableFuture<Boolean> authenticateServer(TLSTrustManager trustManager, CommType comm) {
        byte[] challenge = new byte[32];
        random.nextBytes(challenge);
        FlatBufferBuilder builder = new FlatBufferBuilder();
        builder.finish(
                ServerAuthenticationRequest.createServerAuthenticationRequest(
                        builder,
                        ServerAuthenticationRequest.createChallengeVector(builder, challenge)
                )
        );
        CompletableFuture<ServerAuthenticationResponse> responseFuture = enqueueOrCall(LwtAPI::authenticateServer, builder.dataBuffer(), comm);
        return responseFuture.thenApply(authResponse -> {
            try {
                X509Certificate[] certChain = trustManager.loadCertificates(authResponse.certificateAsByteBuffer());
                if (trustManager.isCertificateChainTrusted(certChain)) {
                    byte[] challengeResponse = ByteBufferUtils.toByteArray(authResponse.responseAsByteBuffer());
                    for (X509Certificate cert : certChain) {
                        if (trustManager.verifySignature(challenge, challengeResponse, "NONE", cert)) {
                            return true;
                        }
                    }
                }
            } catch (GeneralSecurityException | IOException e) {
                Log.e(TAG, "Server authentication failed", e);
            }
            return false;
        });
    }

    public CompletableFuture<TripRouteInfo> getTripRouteInfo(CommType comm) {
        return enqueueOrCall(LwtAPI::getTripRouteInfo, comm);
    }

    public CompletableFuture<TicketValidationInfo> getTicketValidationInfo(CommType comm) {
        return enqueueOrCall(LwtAPI::getTicketValidationInfo, comm);
    }
}
