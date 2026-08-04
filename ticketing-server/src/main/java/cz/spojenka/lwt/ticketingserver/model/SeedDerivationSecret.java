package cz.spojenka.lwt.ticketingserver.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.security.SecureRandom;

@Entity
public class SeedDerivationSecret {

    private static final SecureRandom VALUE_RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    private long validFrom;
    private long validTo;
    private byte[] data;

    SeedDerivationSecret() {

    }

    public SeedDerivationSecret(long validFrom, long validTo, byte[] data) {
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.data = data;
    }

    public static SeedDerivationSecret random(long validFrom, long validTo) {
        byte[] data = new byte[32];
        VALUE_RANDOM.nextBytes(data);
        return new SeedDerivationSecret(validFrom, validTo, data);
    }

    public long getId() {
        return id;
    }

    public long getValidFrom() {
        return validFrom;
    }

    public long getValidTo() {
        return validTo;
    }

    public byte[] getData() {
        return data;
    }
}
