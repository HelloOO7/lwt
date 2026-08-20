package cz.spojenka.lwt.ticketingserver.controllers;

import cz.spojenka.lwt.ticketingserver.api.InspectionSecretResponse;
import cz.spojenka.lwt.ticketingserver.services.SeedDerivationService;
import cz.spojenka.lwt.ticketingserver.services.TicketSigningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.StringWriter;
import java.security.PublicKey;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
@Tag(name = "Ticket inspection", description = "Ticket inspection API")
public class InspectionController {

    private final SeedDerivationService seedDerivationService;
    private final TicketSigningService signingService;

    @Value("${tickets.max-validity-backlog}")
    private Duration ticketValidityMaxBacklog;

    public InspectionController(SeedDerivationService seedDerivationService, TicketSigningService signingService) {
        this.seedDerivationService = seedDerivationService;
        this.signingService = signingService;
    }

    @GetMapping("/inspection/public-keys/pem")
    @Operation(
            summary = "Get public keys for authenticating tickets",
            description = "Get a list of all currently valid public keys to use for ticket authentication. " +
                    "This method returns them as PEM plaintext. The endpoint is publicly accessible, as we don't " +
                    "play on security through obscurity around these parts."
    )
    public List<String> getPublicKeysAsPem() throws IOException {
        List<PublicKey> keys = signingService.getAllVerificationKeys();
        List<String> pems = new ArrayList<>(keys.size());
        for (PublicKey key : keys) {
            StringWriter string = new StringWriter();
            try (PemWriter writer = new PemWriter(string)) {
                writer.writeObject(new PemObject("PUBLIC KEY", key.getEncoded()));
            }
            pems.add(string.toString());
        }
        return pems;
    }

    @GetMapping("/inspection/public-keys/der")
    @Operation(
            summary = "Get public keys for authenticating tickets",
            description = "Get a list of all currently valid public keys to use for ticket authentication. " +
                    "This method returns them as DER binary. The endpoint is publicly accessible, as we don't play " +
                    "on security through obscurity around these parts."
    )
    public List<byte[]> getPublicKeysAsDer() {
        List<PublicKey> keys = signingService.getAllVerificationKeys();
        List<byte[]> ders = new ArrayList<>(keys.size());
        for (PublicKey key : keys) {
            ders.add(key.getEncoded());
        }
        return ders;
    }

    @GetMapping("/inspection/secrets")
    @Secured("TICKET_INSPECTOR")
    @Operation(
            summary = "Get secrets for inspection TOTP checks",
            description = "Get a list of cryptographic secrets necessary for TOTP validation during ticket inspection. The data " +
                    "is scoped by day of ticket validity start and is returned with a server-side configured backlog (to validate " +
                    " multi-day tickets). This method is only accessible to ticket inspectors with a valid client certificate. These " +
                    "secrets must not be available to third parties under no circumstances, as it would allow for ticket theft by photography.",
            security = {@SecurityRequirement(name = "certificate", scopes = {"TICKET_INSPECTOR"})}
    )
    public List<InspectionSecretResponse> getInspectionSecrets() {
        seedDerivationService.ensureSecretForCurrentTime();
        return seedDerivationService.getAllSecretsSince(ZonedDateTime.now().minus(ticketValidityMaxBacklog).toInstant())
                .stream()
                .map(secret -> new InspectionSecretResponse(
                        SeedDerivationService.tsToInstant(secret.getValidFrom()),
                        SeedDerivationService.tsToInstant(secret.getValidTo()),
                        secret.getData()
                ))
                .toList();
    }
}
