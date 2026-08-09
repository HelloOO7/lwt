package cz.spojenka.lwt;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.util.Log;

import com.google.flatbuffers.FlatBufferBuilder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

import cz.spojenka.lwdn.BluetoothLwdnAddress;
import cz.spojenka.lwdn.LwdnAddress;
import cz.spojenka.lwt.util.ByteBufferUtils;
import cz.spojenka.lwt.util.LwtTime;
import cz.spojenka.lwt.util.RTTExecutionObserver;
import cz.spojenka.lwt.util.RemoteTime;
import cz.spojenka.lwt.util.TLSTrustManager;

public class LwtAPIClient extends LwtClient {

    private static final String TAG = "LwtAPIClient";

    private static final int BLUETOOTH_PSM = LwtServiceConstants.BLE_API_PSM;

    private final LwtAPI api;
    private final SecureRandom random = new SecureRandom();

    public LwtAPIClient(Context context, LwdnAddress address) {
        super(context, address);
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

    private static final byte[] SERVER_CHALLENGE_SALT = "LwtServerAuthentication".getBytes(StandardCharsets.US_ASCII);

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
                byte[] saltedChallenge = new byte[SERVER_CHALLENGE_SALT.length + challenge.length];
                System.arraycopy(SERVER_CHALLENGE_SALT, 0, saltedChallenge, 0, SERVER_CHALLENGE_SALT.length);
                System.arraycopy(challenge, 0, saltedChallenge, SERVER_CHALLENGE_SALT.length, challenge.length);

                X509Certificate[] certChain = trustManager.loadCertificates(authResponse.certificateAsByteBuffer());
                if (trustManager.isCertificateChainTrusted(certChain)) {
                    if (trustManager.isDNSNameMatched(certChain, getPeerAddress().getLocalHostName())) {
                        byte[] challengeResponse = ByteBufferUtils.toByteArray(authResponse.responseAsByteBuffer());
                        for (X509Certificate cert : certChain) {
                            if (trustManager.verifySignature(saltedChallenge, challengeResponse, "SHA256", cert)) {
                                return true;
                            }
                        }
                    } else {
                        Log.e(TAG, "Server certificate DNS name does not match expected hostname: " + getPeerAddress().getLocalHostName());
                    }
                } else {
                    Log.e(TAG, "Server certificate chain is not trusted");
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

    private ByteBuffer createPreauthorizationTokensRequest(List<byte[]> activationTokenHashes) {
        FlatBufferBuilder builder = new FlatBufferBuilder();
        int[] activationTokensOffsets = new int[activationTokenHashes.size()];
        for (int i = 0; i < activationTokenHashes.size(); i++) {
            activationTokensOffsets[i] = ActivationToken.createActivationToken(builder, ActivationToken.createDataVector(builder, activationTokenHashes.get(i)));
        }
        builder.finish(
                PreauthorizationTokenRequest.createPreauthorizationTokenRequest(
                        builder,
                        PreauthorizationTokenRequest.createActivationTokensVector(builder, activationTokensOffsets)
                )
        );
        return builder.dataBuffer();
    }

    public CompletableFuture<TokenWithExpiration<Map<byte[], PreauthorizationTokenResult>>> requestPreauthorizationTokens(List<byte[]> activationTokenHashes, CommType comm) {
        CompletableFuture<PreauthorizationTokenResponse> responseFuture = enqueueOrCall(LwtAPI::createPreauthorizationToken, createPreauthorizationTokensRequest(activationTokenHashes), comm);
        RTTExecutionObserver rtt = new RTTExecutionObserver(responseFuture);
        addExecutionObserver(rtt);
        responseFuture.whenCompleteAsync((r, e) -> removeExecutionObserver(rtt));
        return responseFuture.thenApply(response -> {
            Map<byte[], PreauthorizationTokenResult> tokenMap = new HashMap<>();
            for (int i = 0; i < response.tokensLength(); i++) {
                PreauthorizationTokenResult token = response.tokens(i);
                if (token != null) {
                    tokenMap.put(activationTokenHashes.get(i), token);
                }
            }

            RemoteTime remoteTime = new RemoteTime(rtt.getRoundTripStartTime(), Instant.ofEpochMilli(response.issuedAt()), rtt.getRoundTripDuration());

            return new TokenWithExpiration<>(
                    remoteTime.remoteToLocal(Instant.ofEpochMilli(response.issuedAt())),
                    remoteTime.remoteToLocal(Instant.ofEpochMilli(response.expiresAt())),
                    tokenMap
            );
        });
    }

    public CompletableFuture<TokenWithExpiration<PreauthorizationTokenResult>> requestPreauthorizationToken(byte[] activationToken, CommType comm) {
        return requestPreauthorizationTokens(List.of(activationToken), comm)
                .thenApply(tokenMap -> new TokenWithExpiration<>(
                        tokenMap.issuedAt(), tokenMap.expiresAt(), tokenMap.token().get(activationToken)
                ));
    }

    public CompletableFuture<TicketActivationResponse> activateTicket(TicketActivationParams params, CommType comm) {
        FlatBufferBuilder builder = new FlatBufferBuilder();
        int activationTokenOffset = ActivationToken.createActivationToken(builder, ActivationToken.createDataVector(builder, params.getActivationToken()));
        int zonesOffset = params.getActivationZones() != null ? builder.createString(String.join(",", params.getActivationZones())) : -1;
        int appIdOffset = builder.createString(params.getActivationAppId());
        int preauthTokenOffset = params.getPreauthorizationToken() != null ? PreauthorizationToken.createPreauthorizationToken(builder, PreauthorizationToken.createDataVector(builder, params.getPreauthorizationToken())) : -1;
        TicketActivationRequest.startTicketActivationRequest(builder);
        TicketActivationRequest.addActivationToken(builder, activationTokenOffset);
        if (params.getActivationTime() != null) {
            TicketActivationRequest.addActivationTime(builder, LwtTime.createLocalDateTime(builder, params.getActivationTime()));
        }
        TicketActivationRequest.addActivateNowIfEarlier(builder, params.isActivateNowIfEarlier());
        if (zonesOffset >= 0) {
            TicketActivationRequest.addActivationZones(builder, zonesOffset);
        }
        TicketActivationRequest.addActivationAppId(builder, appIdOffset);
        if (preauthTokenOffset >= 0) {
            TicketActivationRequest.addPreauthorizationToken(builder, preauthTokenOffset);
        }
        builder.finish(TicketActivationRequest.endTicketActivationRequest(builder));

        return enqueueOrCall(LwtAPI::activateTicket, builder.dataBuffer(), comm);
    }
}
