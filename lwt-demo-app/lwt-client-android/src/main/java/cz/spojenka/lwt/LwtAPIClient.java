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

    private <T> LwtCall<T> newCall(Function<LwtAPI, LwtCall<T>> operation) {
        return operation.apply(api);
    }

    private <T> LwtCall<T> newCall(BiFunction<LwtAPI, ByteBuffer, LwtCall<T>> operation, ByteBuffer request) {
        return operation.apply(api, request);
    }

    public LwtCall<PingResponse> ping() {
        return newCall(LwtAPI::ping);
    }

    private static final byte[] SERVER_CHALLENGE_SALT = "LwtServerAuthentication".getBytes(StandardCharsets.US_ASCII);

    public LwtCall<Boolean> authenticateServer(TLSTrustManager trustManager) {
        byte[] challenge = new byte[32];
        random.nextBytes(challenge);
        FlatBufferBuilder builder = new FlatBufferBuilder();
        builder.finish(
                ServerAuthenticationRequest.createServerAuthenticationRequest(
                        builder,
                        ServerAuthenticationRequest.createChallengeVector(builder, challenge)
                )
        );
        LwtCall<ServerAuthenticationResponse> responseFuture = newCall(LwtAPI::authenticateServer, builder.dataBuffer());
        return responseFuture.map(authResponse -> {
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

    public LwtCall<TripRouteInfo> getTripRouteInfo() {
        return newCall(LwtAPI::getTripRouteInfo);
    }

    public LwtCall<TicketValidationInfo> getTicketValidationInfo() {
        return newCall(LwtAPI::getTicketValidationInfo);
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

    public LwtCall<Map<byte[], TokenWithExpiration<PreauthorizationTokenResult>>> requestPreauthorizationTokens(List<byte[]> activationTokenHashes) {
        LwtCall<PreauthorizationTokenResponse> responseFuture = newCall(LwtAPI::createPreauthorizationToken, createPreauthorizationTokensRequest(activationTokenHashes));
        RTTExecutionObserver rtt = new RTTExecutionObserver(responseFuture);
        addExecutionObserver(rtt);
        responseFuture.onFinished(() -> removeExecutionObserver(rtt));
        return responseFuture.map(response -> {
            RemoteTime remoteTime = new RemoteTime(rtt.getRoundTripStartTime(), Instant.ofEpochMilli(response.issuedAt()), rtt.getRoundTripDuration());

            Instant issuedAt = remoteTime.remoteToLocal(Instant.ofEpochMilli(response.issuedAt()));

            Map<byte[], TokenWithExpiration<PreauthorizationTokenResult>> tokenMap = new HashMap<>();
            for (int i = 0; i < response.tokensLength(); i++) {
                PreauthorizationTokenResult token = response.tokens(i);
                if (token != null) {
                    tokenMap.put(activationTokenHashes.get(i), new TokenWithExpiration<>(
                            issuedAt,
                            remoteTime.remoteToLocal(Instant.ofEpochMilli(token.expiresAt())),
                            token
                    ));
                }
            }

            return tokenMap;
        });
    }

    public LwtCall<TokenWithExpiration<PreauthorizationTokenResult>> requestPreauthorizationToken(byte[] activationToken) {
        return requestPreauthorizationTokens(List.of(activationToken))
                .map(tokenMap -> tokenMap.get(activationToken));
    }

    public LwtCall<TicketActivationResponse> activateTicket(TicketActivationParams params) {
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

        return newCall(LwtAPI::activateTicket, builder.dataBuffer());
    }

    public LwtCall<SetRazziaResponse> setRazzia(boolean razziaState) {
        FlatBufferBuilder builder = new FlatBufferBuilder();
        builder.finish(SetRazziaRequest.createSetRazziaRequest(builder, razziaState));
        return newCall(LwtAPI::setRazzia, builder.dataBuffer());
    }
}
