package cz.spojenka.lwt.demoapp;

import android.app.Application;
import android.content.SharedPreferences;
import android.location.Location;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import androidx.annotation.NonNull;
import cz.dpp.praguepublictransport.etd.ETDUtils;
import cz.dpp.praguepublictransport.etd.LitackaETD;
import cz.spojenka.android.settings.SharedPrefsHelper;
import cz.spojenka.android.system.GlobalLocationObserver;
import cz.spojenka.android.util.AsyncUtils;
import cz.spojenka.lwt.LwtTicketMetadata;
import cz.spojenka.lwt.ticketing.api.InspectionEventData;
import cz.spojenka.lwt.ticketing.api.InspectionSecretResponse;
import cz.spojenka.lwt.ticketing.api.TelemetryEventBatch;
import cz.spojenka.lwt.ticketing.api.TelemetryEventClass;
import cz.spojenka.lwt.ticketing.api.TelemetryEventPush;
import cz.spojenka.lwt.ticketing.client.InspectionAPI;
import cz.spojenka.lwt.ticketing.client.TelemetryAPI;
import cz.spojenka.lwt.ticketing.client.TicketingClient;
import cz.spojenka.lwt.util.PIDTicketTOTP;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.HttpException;
import retrofit2.Response;

public class TicketInspectionRepository {

    private static final String PK_INSPECTION_SECRETS = "inspection_secrets";
    private static final String PK_PUBLIC_KEYS = "public_keys";
    private static final String PK_STAFF_NUMBER = "staff_number";

    private static final String TAG = "TicketInspection";

    private static TicketInspectionRepository INSTANCE;

    private final SharedPreferences prefs;

    private InspectionAPI remote;
    private TelemetryAPI telemetryAPI;

    private List<InspectionSecretResponse> inspectionSecrets;
    private List<byte[]> publicKeys;
    private List<PublicKey> decodedPublicKeys;

    private GlobalLocationObserver locationObserver;

    private TicketInspectionRepository(Application appContext) {
        prefs = appContext.getSharedPreferences("ticket_inspection", Application.MODE_PRIVATE);
        TicketingClient client = new TicketingClient(BuildConfig.TICKETING_SERVER_URL, GlobalTrustManager.createMosNetworkClient(appContext));
        remote = client.getInspectionAPI();
        telemetryAPI = client.getTelemetryAPI();
        locationObserver = new GlobalLocationObserver(appContext, 1000, 10);
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

    public byte[] getInspectionSecretForTime(Instant time) {
        for (InspectionSecretResponse secret : getInspectionSecrets()) {
            if (!secret.validFrom().isAfter(time) && time.isBefore(secret.validTo())) {
                return secret.data();
            }
        }
        return null;
    }

    public boolean hasSecretForTime(Instant time) {
        return getInspectionSecretForTime(time) != null;
    }

    public boolean isCicoTicket(LitackaETD ticketData) {
        return isCicoTicket(new TicketETDParser(ticketData));
    }

    public boolean isCicoTicket(TicketETDParser ticketData) {
        return ticketData.getCicoSessionId() != null; //CICO session ID
    }

    public Boolean checkCicoTripKeyMatch(LitackaETD ticketData, String tripKey) {
        try {
            LwtTicketMetadata parsed = new TicketETDParser(ticketData).parseLwtMetadata();
            String ticketTripKey = parsed.getTripKey();
            if (ticketTripKey != null) {
                return Objects.equals(ticketTripKey, tripKey);
            } else {
                return null;
            }
        } catch (IllegalArgumentException ex) {
            Log.e(TAG, "Failed to parse ticket metadata", ex);
            return null;
        }
    }

    public TicketInspectionResult verifyTicketAuthenticity(LitackaETD ticketData, Instant totpInstant) {
        return verifyTicketAuthenticity(ticketData, totpInstant, null, null);
    }

    public TicketInspectionResult verifyTicketAuthenticity(LitackaETD ticketData, Instant totpInstant, PublicKey key, byte[] derivationSecret) {
        if (!checkTicketSignature(ticketData, key)) {
            return TicketInspectionResult.INVALID_SIGNATURE;
        }
        if (!checkTicketRotationData(ticketData, derivationSecret, totpInstant)) {
            return TicketInspectionResult.INVALID_TOTP;
        }
        return TicketInspectionResult.OK;
    }

    private boolean checkTicketSignature(LitackaETD ticketData, PublicKey key) {
        byte[] signature = bindEtd(ticketData).getDecodedSignature();
        if (signature == null) {
            Log.w(TAG, "Ticket signature is missing");
            return false;
        }

        LitackaETD innerData = new LitackaETD(ticketData);

        innerData.removeProperty("X-TOTP");
        innerData.removeProperty(ETDUtils.TS_PROPERTY);
        innerData.removeProperty(ETDUtils.SIGNATURE_PROPERTY);

        byte[] rawTicketData = innerData.encode().getBytes(StandardCharsets.UTF_8);

        boolean verifyResult;
        if (key != null) {
            verifyResult = validateSignature(rawTicketData, signature, key);
        } else {
            verifyResult = validateSignature(rawTicketData, signature);
        }

        if (!verifyResult) {
            Log.w(TAG, "Ticket signature validation failed");
            return false;
        }

        return true;
    }

    private boolean checkTicketRotationData(LitackaETD ticketData, byte[] derivationSecret, Instant totpInstant) {
        if (isCanTicketNotHaveTimestamp(ticketData)) {
            return true;
        }

        String totp = bindEtd(ticketData).getTOTP();
        String ts = ETDUtils.getTimestampHash(ticketData);
        if (!isAllowLegacyRotationScheme(ticketData) && ts != null) {
            Log.w(TAG, "X-TS property was set on a non-legacy ticket (downgrade attack attempt?)");
            ts = null;
        }

        if (totp == null && ts == null) {
            Log.w(TAG, "Ticket TOTP/TS is missing");
            return false;
        }

        if (totp != null) {
            if (checkTicketTOTP(ticketData, derivationSecret, totpInstant, totp)) {
                return true;
            }
            Log.w(TAG, "No TOTP matched ticket value: " + totp);
        }
        if (ts != null) {
            if (checkTicketTSLegacy(ticketData, totpInstant, ts)) {
                return true;
            }
            Log.w(TAG, "No TS matched ticket value: " + ts);
        }

        return false;
    }

    private boolean checkTicketTOTP(LitackaETD etd, byte[] derivationSecret, Instant totpInstant, String totp) {
        if (derivationSecret == null) {
            String vs = etd.getProperty("VS");
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
            derivationSecret = getInspectionSecretForTime(validityStart.toInstant());
        }
        if (derivationSecret == null) {
            Log.w(TAG, "No inspection secret available for the given time");
            return false;
        }

        PIDTicketTOTP totpChecker = new PIDTicketTOTP(PIDTicketTOTP.deriveSeedSecret(bindEtd(etd).getDecodedSignature(), derivationSecret));

        for (int i = -1; i <= 1; i++) {
            Instant checkTime = totpInstant.plusSeconds(i * totpChecker.getRefreshInterval().getSeconds());
            String expectedTotp = totpChecker.generatePasswordString(checkTime);
            if (expectedTotp.equals(totp)) {
                return true;
            }
        }

        return false;
    }

    private boolean checkTicketTSLegacy(LitackaETD etd, Instant tsInstant, String ts) {
        String expectedTs = ETDUtils.createTimestampHash(bindEtd(etd).getSignature(), tsInstant);
        return expectedTs != null && expectedTs.equals(ts);
    }

    private boolean isCanTicketNotHaveTimestamp(LitackaETD etd) {
        return "PAPER".equals(bindEtd(etd).getTicketType()); // paper IDOL tickets from POP
    }

    private boolean isAllowLegacyRotationScheme(LitackaETD etd) {
        // only allow legacy (insecure) X-TS timestamps on non-LWT tickets
        return bindEtd(etd).getLwtMetadata() == null;
    }

    public TicketInspectionResult verifyTicketTimeValidity(Instant time, LitackaETD etd) {
        TicketETDParser parser = bindEtd(etd);
        if (parser.getValidSince() != null && time.isBefore(parser.getValidSince().toInstant())) {
            return TicketInspectionResult.BEFORE_VALIDITY;
        }
        if (parser.getValidUntil() != null && time.isAfter(parser.getValidUntil().toInstant())) {
            return TicketInspectionResult.PAST_VALIDITY;
        }
        return TicketInspectionResult.OK;
    }

    public boolean isCicoCheckedOut(LitackaETD etd, Set<UUID> blacklist) {
        // this should only be called on a trusted ticket, so data absence is not checked
        UUID uuid = UUID.fromString(bindEtd(etd).getCicoSessionId());
        return !blacklist.contains(uuid);
    }

    private TicketETDParser bindEtd(LitackaETD etd) {
        return new TicketETDParser(etd);
    }

    public boolean isStaffNumberSet() {
        return prefs.contains(PK_STAFF_NUMBER);
    }

    public int getStaffNumber() {
        return prefs.getInt(PK_STAFF_NUMBER, 0);
    }

    public void setStaffNumber(int staffNumber) {
        prefs.edit().putInt(PK_STAFF_NUMBER, staffNumber)
                .apply();
    }

    public void clearStaffNumber() {
        prefs.edit().remove(PK_STAFF_NUMBER)
                .apply();
    }

    public void logTicketScanEvent(Instant time, LitackaETD etd, TicketInspectionResult result) {
        Location location = locationObserver.getLastLocation().getValue();
        TicketETDParser parser = bindEtd(etd);
        String tid = parser.getTicketId() != null ? parser.getTicketId().toString() : parser.getCicoSessionId();
        TelemetryEventPush event = new TelemetryEventPush(
                TelemetryEventClass.INSPECTION,
                isStaffNumberSet() ? String.valueOf(getStaffNumber()) : null,
                time.toEpochMilli(),
                time.atZone(ZoneId.systemDefault()).toOffsetDateTime(),
                "TICKET_INSPECTED",
                tid,
                new InspectionEventData(result.name(), location != null ? location.getLatitude() : 0, location != null ? location.getLongitude() : 0)
        );
        uploadTelemetryEvent(event);
    }

    private void uploadTelemetryEvent(TelemetryEventPush event) {
        telemetryAPI.pushEvents(new TelemetryEventBatch(List.of(event), Instant.now().toEpochMilli())).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(@NonNull Call<ResponseBody> call, @NonNull Response<ResponseBody> response) {

            }

            @Override
            public void onFailure(@NonNull Call<ResponseBody> call, @NonNull Throwable t) {
                Log.e(TAG, "Failed to upload telemetry event", t);
            }
        });
    }

    public void startLocationObserver() {
        locationObserver.wake();
    }

    public void stopLocationObserver() {
        locationObserver.sleep();
    }
}
