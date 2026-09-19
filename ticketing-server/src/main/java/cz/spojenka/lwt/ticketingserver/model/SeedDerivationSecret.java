package cz.spojenka.lwt.ticketingserver.model;

import cz.spojenka.lwt.ticketingserver.services.RandomGenerator;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class SeedDerivationSecret {

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
        return new SeedDerivationSecret(validFrom, validTo, RandomGenerator.bytes(32));
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
