package cz.spojenka.lwt.util;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

public class PIDTicketTOTP extends TicketTOTP {

    public PIDTicketTOTP(byte[] secret) {
        super(secret, TimeBasedOneTimePasswordGenerator.TOTP_ALGORITHM_HMAC_SHA256, 6, Duration.ofSeconds(30));
    }

    public static byte[] deriveSeedSecret(byte[] ticketSignature, byte[] derivationSecret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(ticketSignature);
            digest.update(derivationSecret);
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
