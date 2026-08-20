package cz.spojenka.lwt.util;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class TicketTOTP {

    private final TimeBasedOneTimePasswordGenerator totp;
    private final SecretKey secretKey;

    public TicketTOTP(byte[] secret, String hmacAlgorithm, int passwordLength, Duration refreshInterval) {
        try {
            totp = new TimeBasedOneTimePasswordGenerator(refreshInterval, passwordLength, hmacAlgorithm);
            secretKey = new SecretKeySpec(secret, totp.getAlgorithm());
        } catch (NoSuchAlgorithmException e) {
            throw new TOTPException(e);
        }
    }

    public Duration getRefreshInterval() {
        return totp.getTimeStep();
    }

    public int generatePassword(Instant time) {
        try {
            return totp.generateOneTimePassword(secretKey, time);
        } catch (InvalidKeyException e) {
            throw new TOTPException(e);
        }
    }

    public String generatePasswordString(Instant time) {
        try {
            return totp.generateOneTimePasswordString(secretKey, time);
        } catch (InvalidKeyException e) {
            throw new TOTPException(e);
        }
    }

    public static class TOTPException extends RuntimeException {

        public TOTPException(Throwable cause) {
            super(cause);
        }
    }
}
