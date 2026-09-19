package cz.spojenka.lwt.ticketingserver.services;

import java.security.SecureRandom;

public class RandomGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static byte[] bytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
