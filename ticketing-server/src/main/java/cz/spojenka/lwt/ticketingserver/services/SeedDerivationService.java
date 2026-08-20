package cz.spojenka.lwt.ticketingserver.services;

import cz.dpp.praguepublictransport.etd.ETDUtils;
import cz.dpp.praguepublictransport.etd.LitackaETD;
import cz.spojenka.lwt.ticketingserver.model.SeedDerivationRepository;
import cz.spojenka.lwt.ticketingserver.model.SeedDerivationSecret;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Service;
import shaded.org.apache.commons.codec.digest.DigestUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class SeedDerivationService {

    private static final long SECRET_VALIDITY_INTERVAL = Duration.ofDays(1).toSeconds();

    private final SeedDerivationRepository repository;

    public SeedDerivationService(SeedDerivationRepository repository) {
        this.repository = repository;
    }

    private SeedDerivationSecret getOrCreateSecretForTime(long time) {
        SeedDerivationSecret secret = repository.getForTime(time);
        if (secret == null) {
            SeedDerivationSecret latest = repository.getLatest();
            while (secret == null || time >= secret.getValidTo()) {
                // create all secrets until the requested time is covered
                long validStart = latest != null ? latest.getValidTo() : getUTCStartOfDay(time);
                secret = SeedDerivationSecret.random(validStart, validStart + SECRET_VALIDITY_INTERVAL);
                secret = repository.save(secret);
                latest = secret;
            }
        }
        return secret;
    }

    private long getUTCStartOfDay(long ts) {
        return Instant.ofEpochSecond(ts).atOffset(ZoneOffset.UTC).toLocalDate().atStartOfDay().toEpochSecond(ZoneOffset.UTC);
    }

    private long getSecretTimeForEtd(LitackaETD etd) {
        return OffsetDateTime.parse(etd.getProperty("VS")).toEpochSecond();
    }

    private byte[] getTicketSignature(LitackaETD etd) {
        return ETDUtils.getDecodedTicketSignature(etd);
    }

    public byte[] deriveTotpSeedForEtd(LitackaETD etd) {
        return DigestUtils.sha256(ArrayUtils.addAll(getTicketSignature(etd), getOrCreateSecretForTime(getSecretTimeForEtd(etd)).getData()));
    }

    public void ensureSecretForCurrentTime() {
        getOrCreateSecretForTime(Instant.now().getEpochSecond());
    }

    public List<SeedDerivationSecret> getAllSecretsSince(Instant instant) {
        return repository.getAllSince(instant.getEpochSecond());
    }

    public static Instant tsToInstant(long ts) {
        return Instant.ofEpochSecond(ts);
    }
}
