package cz.spojenka.lwt.util;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;

import java.time.Duration;

public class PIDTicketTOTP extends TicketTOTP {

    public PIDTicketTOTP(byte[] secret) {
        super(secret, TimeBasedOneTimePasswordGenerator.TOTP_ALGORITHM_HMAC_SHA256, 6, Duration.ofSeconds(30));
    }
}
