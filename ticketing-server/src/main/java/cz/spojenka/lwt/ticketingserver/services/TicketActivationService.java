package cz.spojenka.lwt.ticketingserver.services;

import cz.dpp.praguepublictransport.LitackaUtils;
import cz.dpp.praguepublictransport.etd.ETDTicketType;
import cz.dpp.praguepublictransport.etd.LitackaETD;
import cz.spojenka.lwt.ticketing.api.ProductCategory;
import cz.spojenka.lwt.ticketing.api.TicketActivationParams;
import cz.spojenka.lwt.ticketingserver.api.AccessDeniedException;
import cz.spojenka.lwt.ticketingserver.api.DataArchivedException;
import cz.spojenka.lwt.ticketingserver.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class TicketActivationService {

    private static final short ACTIVATION_TOKEN_VERSION = 1;

    public static final int ACTIVATION_FLAG_DISALLOW_PREAUTH = 1;

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
        byte[] salt = RandomGenerator.bytes(16);

        byte[] token = ByteBuffer.allocate(Short.BYTES + Long.BYTES + Integer.BYTES + salt.length)
                        .putShort(ACTIVATION_TOKEN_VERSION)
                        .putLong(ticket.getId())
                        .putInt(flags)
                        .put(salt)
                        .array();

        return new TicketActivationToken(SecureToken.create(token, 0, signingService::signActivationToken));
    }

    public Ticket activateTicket(Ticket ticket, TicketActivationParams params, boolean isPrivileged, TicketActivationSource source) {
        OffsetDateTime now = OffsetDateTime.now();

        OffsetDateTime actualValidFrom = resolveActivationTime(params, isPrivileged, now);
        if (ticket.getValidSince() != null && actualValidFrom.isAfter(ticket.getValidSince())) {
            // during debug phase, this check is skipped
            //throw new AccessDeniedException("Can not postpone ticket activation time after it has already been activated.");
        }
        LocalDate validFromDate = actualValidFrom.toLocalDate();
        Product product = ticket.getProduct();
        if (validFromDate.isBefore(product.getValidFrom()) || validFromDate.isAfter(product.getValidTo())) {
            throw new DataArchivedException("Ticket is purchased for a tariff that is no longer valid. It should be returned for a refund.");
        }
        List<String> activationZones = LitackaUtils.parseCommaSeparatedList(params.zones());
        List<String> eligibleZones = product.getEligibleZones();
        if (!eligibleZones.isEmpty()) {
            // eligible zones are a restriction - if empty, it is a network-wide product
            if (!Set.copyOf(eligibleZones).containsAll(activationZones)) {
                throw new AccessDeniedException("Attempted to activate ticket for zones: " + activationZones + ", which are not included in the product's eligible zones: " + eligibleZones);
            }
        }
        List<String> excludedZones = product.getExcludedZones();
        if (!excludedZones.isEmpty()) {
            if (CollectionUtils.containsAny(excludedZones, activationZones)) {
                throw new AccessDeniedException("Attempted to activate ticket for zones: " + activationZones + ", but product expressly forbids activation in: " + excludedZones);
            }
        }
        if (product.getZoneCount() == null) {
            activationZones = product.getEligibleZones();
        } else {
            int zoneCount = product.getZoneCount();
            if (activationZones.size() > zoneCount) {
                throw new AccessDeniedException("Product " + product.getId() + " is valid for " + zoneCount + " zones, but an attempt was made to activate it for " + activationZones.size() + " zones: " + activationZones);
            }
            List<String> originalActivationZones = activationZones;
            activationZones = LitackaUtils.ensureZonesContiguous(activationZones);
            if (activationZones.size() > zoneCount) {
                throw new AccessDeniedException("Specified activation zones " + originalActivationZones + " were non-contiguous, and the contiguous set " + activationZones + " exceeds the product's zone count of " + zoneCount);
            }
        }
        activationZones = LitackaUtils.sortZonesForPrint(activationZones);

        TicketActivationRecord activationRecord = new TicketActivationRecord(
                params.appId(),
                now,
                source,
                Objects.requireNonNullElse(params.activationSourceMetadata(), "")
        );
        ticket.activate(actualValidFrom, LitackaUtils.createCommaSeparatedList(activationZones), activationRecord);

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
        etd.setProperty("TT", getEtdTypeFromProductCat(ticket.getProduct().getCategory()).name());
        etd.setProperty("TI", Long.toString(ticket.getId()));
        etd.setProperty("X-AP", Integer.toString(activationProtection));
        etd.setProperty("X-CPTP", Integer.toString(ticket.getProduct().getCptp()));
        TicketActivationRecord ar = ticket.getActivationRecord();
        if (ar != null && ar.getActivationSource() == TicketActivationSource.LWT_VALIDATOR) {
            etd.setProperty("X-LWT", ar.getActivationSourceMetadata());
        }
        return etd;
    }

    private ETDTicketType getEtdTypeFromProductCat(ProductCategory category) {
        return switch (category) {
            case REDUCED, DISABLED -> ETDTicketType.REDUCED;
            case LUGGAGE -> ETDTicketType.BAG;
            case DOG -> ETDTicketType.DOG;
            case SENIOR -> ETDTicketType.SENIOR;
            default -> ETDTicketType.ADULT;
        };
    }
}
