package cz.spojenka.lwt.ticketingserver.controllers;

import cz.spojenka.lwt.ticketingserver.api.AccessDeniedException;
import cz.spojenka.lwt.ticketingserver.api.TicketActivationParams;
import cz.spojenka.lwt.ticketingserver.model.Ticket;
import cz.spojenka.lwt.ticketingserver.model.TicketActivationSource;
import cz.spojenka.lwt.ticketingserver.model.TicketRepository;
import cz.spojenka.lwt.ticketingserver.services.CertificateAuthService;
import cz.spojenka.lwt.ticketingserver.services.TicketActivationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import retrofit2.http.Query;

import java.util.List;

@RestController
@Tag(name = "Tickets", description = "Ticket management API")
public class TicketsController {

    private final TicketRepository repository;
    private final TicketActivationService activationService;

    public TicketsController(TicketRepository repository, TicketActivationService activationService) {
        this.repository = repository;
        this.activationService = activationService;
    }

    @PostMapping("/tickets/new")
    @Secured("TICKET_ISSUER")
    @Operation(
            summary = "Issue a new ticket",
            description = "Create a new ticket for the specified product and holder account. Requires TICKET_ISSUER role.",
            security = {@SecurityRequirement(name = "certificate", scopes = {"TICKET_ISSUER"})}
    )
    public Ticket issueNewTicket(@Query("productId") int productId, @Query("holderAccountId") long holderAccountId) {
        Ticket ticket = new Ticket(productId, holderAccountId);
        activationService.generateActivationToken(ticket, 0);
        ticket = repository.save(ticket);
        return ticket;
    }

    @GetMapping("/tickets")
    @Operation(
            summary = "Get all tickets",
            description = "Returns all tickets of the authenticated account. Currently returns all tickets as account management is not implemented yet."
    )
    public List<Ticket> getTicketsForAccount() {
        // currently return all, accounts not implemented yet
        return repository.findAll();
    }

    @PostMapping("/tickets/{id}/activate")
    @Operation(
            summary = "Activate a ticket",
            description = "Activate a ticket and return ticket data with a validation token. The PRIVILEGED_ACTIVATION " +
                    "role is supported for skipping the security delay."
    )
    public Ticket activateTicket(@PathVariable long id, @RequestBody @Valid TicketActivationParams params, Authentication authentication) {
        return activateTicketImpl(
                id,
                params,
                CertificateAuthService.hasRole(authentication, "PRIVILEGED_ACTIVATION"),
                CertificateAuthService.hasRole(authentication, "LWT_DEVICE") ? TicketActivationSource.LWT_VALIDATOR : TicketActivationSource.USER
        );
    }

    private Ticket activateTicketImpl(long id, TicketActivationParams params, boolean isPrivileged, TicketActivationSource source) {
        if (params.clientIntegrityAttested() && !isPrivileged) {
            throw new AccessDeniedException("Unprivileged caller attempted to attest for client integrity.");
        }
        Ticket ticket = repository.getReferenceById(id);
        ticket = activationService.activateTicket(ticket, params, isPrivileged, source);
        return ticket;
    }
}
