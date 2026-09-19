package cz.spojenka.lwt;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import cz.spojenka.lwt.util.TicketTOTP;

public class PresenceTrackingClient {

    private static final Duration TOTP_PERIOD = Duration.ofMinutes(1);

    private final SecureRandom random = new SecureRandom();

    private final byte[] clientId;
    private final byte[] totpSecret;
    private final TicketTOTP totp;

    public PresenceTrackingClient() {
        clientId = new byte[8];
        random.nextBytes(clientId);
        totpSecret = new byte[32];
        random.nextBytes(totpSecret);
        totp = new TicketTOTP(totpSecret, TimeBasedOneTimePasswordGenerator.TOTP_ALGORITHM_HMAC_SHA256, 8, TOTP_PERIOD);
    }

    public byte[] getClientId() {
        return clientId;
    }

    public void resetClientId() {
        random.nextBytes(clientId);
    }

    public byte[] getTotpSecret() {
        return totpSecret;
    }

    public int getCurrentTotpPassword() {
        return totp.generatePassword(Instant.now());
    }

    public Duration getTotpPeriod() {
        return TOTP_PERIOD;
    }
}
