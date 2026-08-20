package cz.spojenka.lwt.demoapp;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import cz.dpp.praguepublictransport.etd.ETDUtils;
import cz.dpp.praguepublictransport.etd.LitackaETD;
import cz.spojenka.android.settings.SharedPrefsHelper;
import cz.spojenka.android.util.AsyncUtils;
import cz.spojenka.lwt.inspectionapi.InspectionAPI;
import cz.spojenka.lwt.inspectionapi.InspectionSecretResponse;
import cz.spojenka.lwt.util.PIDTicketTOTP;
import retrofit2.Call;
import retrofit2.HttpException;
import retrofit2.Response;

public class TicketInspectionRepository {

    private static final String PK_INSPECTION_SECRETS = "inspection_secrets";
    private static final String PK_PUBLIC_KEYS = "public_keys";

    private static final String TAG = "TicketInspection";

    private static TicketInspectionRepository INSTANCE;

    private final SharedPreferences prefs;

    private InspectionAPI remote;

    private List<InspectionSecretResponse> inspectionSecrets;
    private List<byte[]> publicKeys;
    private List<PublicKey> decodedPublicKeys;

    public TicketInspectionRepository(Application appContext) {
        prefs = appContext.getSharedPreferences("ticket_inspection", Application.MODE_PRIVATE);
        remote = InspectionAPI.create("https://ticketing.mos.ropid:8080", GlobalTrustManager.getInstance(appContext));
    }

    public static TicketInspectionRepository getInstance(Application appContext) {
        if (INSTANCE == null) {
            synchronized (TicketInspectionRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new TicketInspectionRepository(appContext);
                }
            }
        }
        return INSTANCE;
    }

    private synchronized List<InspectionSecretResponse> getInspectionSecrets() {
        if (inspectionSecrets == null) {
            var data = SharedPrefsHelper.loadObject(prefs, PK_INSPECTION_SECRETS, InspectionSecretResponse[].class);
            if (data != null) {
                inspectionSecrets = List.of(data);
            } else {
                inspectionSecrets = List.of();
            }
        }
        return inspectionSecrets;
    }

    private synchronized void setInspectionSecrets(List<InspectionSecretResponse> secrets) {
        inspectionSecrets = secrets;
        SharedPrefsHelper.saveObject(prefs, PK_INSPECTION_SECRETS, secrets.toArray(new InspectionSecretResponse[0]));
    }

    private synchronized List<byte[]> getPublicKeys() {
        if (publicKeys == null) {
            var data = SharedPrefsHelper.loadObject(prefs, PK_PUBLIC_KEYS, byte[][].class);
            if (data != null) {
                publicKeys = List.of(data);
            } else {
                publicKeys = List.of();
            }
        }
        return publicKeys;
    }

    private synchronized void setPublicKeys(List<byte[]> keys) {
        publicKeys = keys;
        decodedPublicKeys = null;
        SharedPrefsHelper.saveObject(prefs, PK_PUBLIC_KEYS, keys.toArray(new byte[0][]));
    }

    private synchronized List<PublicKey> getDecodedPublicKeys() {
        if (decodedPublicKeys == null) {
            try {
                decodedPublicKeys = new ArrayList<>();
                List<byte[]> keysDer = getPublicKeys();
                for (byte[] keyDer : keysDer) {
                    decodedPublicKeys.add(decodePublicKey(keyDer));
                }
            } catch (Exception ex) {
                Log.e(TAG, "Failed to decode public keys", ex);
                decodedPublicKeys = List.of();
            }
        }
        return decodedPublicKeys;
    }

    public static PublicKey decodePublicKey(byte[] encoded) throws InvalidKeySpecException {
        X509EncodedKeySpec spec = new X509EncodedKeySpec(encoded);

        for (String algorithm : new String[]{"EC", "RSA"}) {
            try {
                return KeyFactory.getInstance(algorithm)
                        .generatePublic(spec);
            } catch (InvalidKeySpecException | NoSuchAlgorithmException ignored) {
            }
        }

        throw new InvalidKeySpecException("Unsupported public key algorithm");
    }

    public void syncWithRemote() throws IOException {
        setInspectionSecrets(Objects.requireNonNull(sendRequest(remote.getInspectionSecrets())));
        setPublicKeys(Objects.requireNonNull(sendRequest(remote.getPublicKeys())));
    }

    public CompletableFuture<Void> syncWithRemoteAsync() {
        return AsyncUtils.runAsync(this::syncWithRemote);
    }

    private <T> T sendRequest(Call<T> call) throws IOException {
        Response<T> response = call.execute();
        if (!response.isSuccessful()) {
            throw new IOException(new HttpException(response));
        }
        return response.body();
    }

    private String pubKeyAlgToSigAlg(String alg) {
        if ("EC".equals(alg)) {
            return "ECDSA";
        }
        return alg;
    }

    private boolean validateSignature(byte[] data, byte[] signature, PublicKey publicKey) {
        try {
            Signature verifier = Signature.getInstance("SHA256with" + pubKeyAlgToSigAlg(publicKey.getAlgorithm()));
            verifier.initVerify(publicKey);
            verifier.update(data);
            return verifier.verify(signature);
        } catch (Exception ex) {
            Log.e(TAG, "Failed to verify ticket signature", ex);
            return false;
        }
    }

    private boolean validateSignature(byte[] data, byte[] signature) {
        for (PublicKey publicKey : getDecodedPublicKeys()) {
            if (validateSignature(data, signature, publicKey)) {
                return true;
            }
        }
        return false;
    }

    private byte[] getInspectionSecretForTime(Instant time) {
        for (InspectionSecretResponse secret : getInspectionSecrets()) {
            if (!secret.validFrom.isAfter(time) && !secret.validTo.isBefore(time)) {
                return secret.data;
            }
        }
        return null;
    }

    public boolean verifyTicketAuthenticity(LitackaETD ticketData, Instant totpInstant) {
        String totp = ticketData.getProperty("X-TOTP");
        if (totp == null) {
            Log.w(TAG, "Ticket TOTP is missing");
            return false;
        }
        String vs = ticketData.getProperty("VS");
        if (vs == null) {
            Log.w(TAG, "Ticket VS is missing");
            return false;
        }
        OffsetDateTime validityStart;
        try {
            validityStart = OffsetDateTime.parse(vs);
        } catch (Exception ex) {
            Log.w(TAG, "Failed to parse ticket VS: " + vs, ex);
            return false;
        }
        byte[] derivationSecret = getInspectionSecretForTime(validityStart.toInstant());
        if (derivationSecret == null) {
            Log.w(TAG, "No inspection secret available for the given time");
            return false;
        }
        String signature = ticketData.getProperty(ETDUtils.SIGNATURE_PROPERTY);
        if (signature == null) {
            Log.w(TAG, "Ticket signature is missing");
            return false;
        }
        ticketData.removeProperty("X-TOTP");
        ticketData.removeProperty(ETDUtils.SIGNATURE_PROPERTY);

        byte[] decodedSignature = ETDUtils.decodeTicketSignature(signature);
        byte[] rawTicketData = ticketData.encode().getBytes(StandardCharsets.UTF_8);

        if (!validateSignature(rawTicketData, decodedSignature)) {
            Log.w(TAG, "Ticket signature validation failed");
            return false;
        }

        PIDTicketTOTP totpChecker = new PIDTicketTOTP(PIDTicketTOTP.deriveSeedSecret(decodedSignature, derivationSecret));

        for (int i = -1; i <= 1; i++) {
            Instant checkTime = totpInstant.plusSeconds(i * totpChecker.getRefreshInterval().getSeconds());
            String expectedTotp = totpChecker.generatePasswordString(checkTime);
            if (expectedTotp.equals(totp)) {
                return true;
            }
        }

        Log.w(TAG, "No TOTP matched ticket value: " + totp);

        return false;
    }
}
