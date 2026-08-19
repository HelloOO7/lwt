package cz.spojenka.lwt.ticketingserver.services;

import cz.dpp.praguepublictransport.etd.LitackaETD;
import cz.spojenka.lwt.ticketingserver.api.AccessDeniedException;
import cz.spojenka.lwt.ticketingserver.api.TicketActivationParams;
import cz.spojenka.lwt.ticketingserver.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Service
public class TicketActivationService {

    private static final short ACTIVATION_TOKEN_VERSION = 1;

    public static final int ACTIVATION_FLAG_DISALLOW_PREAUTH = 1;

    private final SecureRandom activationTokenRng = new SecureRandom();

    @Value("${tickets.issuer}")
    private String issuer;
    @Value("${tickets.activation-protection}")
    private int activationProtection;

    private final TicketRepository repository;
    private final TicketSigningService signingService;
    private final SeedDerivationService seedDerivationService;

    public TicketActivationService(TicketRepository repository, TicketSigningService signingService, SeedDerivationService seedDerivationService) {
        this.repository = repository;
        this.signingService = signingService;
        this.seedDerivationService = seedDerivationService;
    }

    public TicketActivationToken generateActivationToken(Ticket ticket, int flags) {
        byte[] salt = new byte[32];
        activationTokenRng.nextBytes(salt);

        byte[] token = ByteBuffer.allocate(Short.BYTES + Long.BYTES + salt.length + Integer.BYTES)
                        .putShort(ACTIVATION_TOKEN_VERSION)
                        .putLong(ticket.getId())
                        .putInt(flags)
                        .put(salt)
                        .putInt(0) // keyID
                        .array();

        byte[] signature = signingService.signActivationToken(token);

        byte[] signedToken = ByteBuffer.allocate(token.length + signature.length + Short.BYTES)
                .put(token)
                .put(signature)
                .putShort((short) signature.length) //will be read from end
                .array();

        return new TicketActivationToken(signedToken);
    }

    public Ticket activateTicket(Ticket ticket, TicketActivationParams params, boolean isPrivileged, TicketActivationSource source) {
        OffsetDateTime now = OffsetDateTime.now();

        OffsetDateTime actualValidFrom = resolveActivationTime(params, isPrivileged, now);
        if (ticket.getValidSince() != null && actualValidFrom.isAfter(ticket.getValidSince())) {
            // during debug phase, this check is skipped
            //throw new AccessDeniedException("Can not postpone ticket activation time after it has already been activated.");
        }

        TicketActivationRecord activationRecord = new TicketActivationRecord(
                params.appId(),
                now,
                source,
                Objects.requireNonNullElse(params.activationSourceMetadata(), "")
        );
        ticket.activate(actualValidFrom, params.zones(), activationRecord);

        LitackaETD etd = signingService.getSignedEtd(generateEtd(ticket));

        ticket.setActivationToken(null);
        ticket.setPayload(new TicketPayload(
                etd.encode().getBytes(StandardCharsets.UTF_8),
                seedDerivationService.deriveTotpSeedForEtd(etd)
        ));

        return repository.save(ticket);
    }

    private OffsetDateTime resolveActivationTime(TicketActivationParams params, boolean isPrivileged, OffsetDateTime now) {
        OffsetDateTime actualActivationTime = params.time();
        if (actualActivationTime == null) {
            actualActivationTime = now;
        }
        if (params.activateNowIfEarlier()) {
            if (now.isBefore(actualActivationTime)) {
                actualActivationTime = now;
            }
        }
        if (!params.clientIntegrityAttested() || !isPrivileged) {
            OffsetDateTime minActivationTime = now.plusSeconds(activationProtection);
            if (actualActivationTime.isBefore(minActivationTime)) {
                actualActivationTime = minActivationTime;
            }
        }
        return actualActivationTime;
    }

    private LitackaETD generateEtd(Ticket ticket) {
        LitackaETD etd = new LitackaETD(1);
        etd.setProperty("IN", issuer);
        etd.setProperty("VS", Objects.requireNonNull(ticket.getValidSince()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        etd.setProperty("VU", Objects.requireNonNull(ticket.getValidUntil()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        etd.setProperty("VZ", ticket.getValidZones());
        //etd.setProperty("TT", );
        etd.setProperty("TI", Long.toString(ticket.getId()));
        etd.setProperty("X-AP", Integer.toString(activationProtection));
        //etd.setProperty("X-CPTP", );
        TicketActivationRecord ar = ticket.getActivationRecord();
        if (ar != null && ar.getActivationSource() == TicketActivationSource.LWT_VALIDATOR) {
            etd.setProperty("X-LWT", ar.getActivationSourceMetadata());
        }
        return etd;
    }
}
