package cz.spojenka.lwt.ticketingserver.controllers;

import cz.spojenka.lwt.ticketing.api.TicketActivationParams;
import cz.spojenka.lwt.ticketing.api.TicketPayloadResponse;
import cz.spojenka.lwt.ticketing.api.TicketResponse;
import cz.spojenka.lwt.ticketingserver.api.AccessDeniedException;
import cz.spojenka.lwt.ticketingserver.api.NotFoundException;
import cz.spojenka.lwt.ticketingserver.model.*;
import cz.spojenka.lwt.ticketingserver.services.AccountService;
import cz.spojenka.lwt.ticketingserver.services.CertificateAuthService;
import cz.spojenka.lwt.ticketingserver.services.TicketActivationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import retrofit2.http.Query;

import java.util.List;
import java.util.Objects;

@RestController
@Tag(name = "Tickets", description = "Ticket management API")
public class TicketsController {

    private final TicketRepository repository;
    private final TicketActivationService activationService;
    private final AccountService accountService;

    public TicketsController(TicketRepository repository, TicketActivationService activationService, AccountService accountService) {
        this.repository = repository;
        this.activationService = activationService;
        this.accountService = accountService;
    }

    @PostMapping("/tickets/new")
    @Secured(Privileges.TICKET_ISSUER)
    @Operation(
            summary = "Issue a new ticket",
            description = "Create a new ticket for the specified product and holder account. Requires TICKET_ISSUER role.",
            security = {@SecurityRequirement(name = "certificate", scopes = {Privileges.TICKET_ISSUER})}
    )
    public TicketResponse issueNewTicket(@Query("productId") int productId, @Query("holderAccountId") long holderAccountId) {
        Ticket ticket = new Ticket(productId, accountService.findByIdOrThrow(holderAccountId));
        activationService.generateActivationToken(ticket, 0);
        ticket = repository.save(ticket);
        return createTicketResponse(ticket, true, true);
    }

    @GetMapping("/tickets/{id}")
    @Operation(
            summary = "Get ticket by ID",
            description = "Returns ticket data for the specified ticket ID. If the caller is not privileged, tickets can only be "
                        + "accessed by their owners. To return payloads for unprivileged callers, the appId must be provided and match the ticket's activation record. "
                        + "The activationAppId in the response is never returned to unprivileged users."
    )
    public TicketResponse getTicketById(@PathVariable long id, @RequestParam(value = "appId", required = false) String appId, Authentication authentication) {
        Ticket ticket = getTicketOrThrow(id);
        boolean privileged = CertificateAuthService.hasRole(authentication, Privileges.TICKET_ISSUER);
        boolean canShowPayloads = false;
        if (!privileged) {
            if (AccountService.checkAccess(authentication, ticket.getHolderAccount().getId())) {
                if (appId != null) {
                    canShowPayloads = Objects.equals(appId, ticket.getActivationRecord() != null ? ticket.getActivationRecord().getAppId() : null);
                }
            } else {
                throw new AccessDeniedException("Unprivileged caller attempted to access a ticket that isn't theirs!");
            }
        } else {
            canShowPayloads = true;
        }
        return createTicketResponse(ticket, privileged, canShowPayloads);
    }

    private Ticket getTicketOrThrow(long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("No such ticket: " + id));
    }

    @GetMapping("/tickets")
    @Operation(
            summary = "Get all tickets",
            description = "Returns all tickets of the authenticated account. "
                    + "To return payloads for activate tickets, an app ID must be provided and match the tickets' activation records."
    )
    public List<TicketResponse> getTicketsForAccount(@RequestParam(value = "appId", required = false) String appId, @AuthenticationPrincipal AccountPrincipal accountPrincipal) {
        if (accountPrincipal == null || !accountPrincipal.accountExists()) {
            return List.of();
        } else {
            return repository.findAllByHolderAccount(accountPrincipal.requireAccount())
                    .stream()
                    .map(ticket -> {
                        boolean canShowPayloads = false;
                        if (appId != null) {
                            canShowPayloads = Objects.equals(appId, ticket.getActivationRecord() != null ? ticket.getActivationRecord().getAppId() : null);
                        }
                        return createTicketResponse(ticket, false, canShowPayloads);
                    })
                    .toList();
        }
    }

    @PostMapping("/tickets/{id}/activate")
    @Operation(
            summary = "Activate a ticket",
            description = "Activate a ticket and return ticket data with a validation token. The PRIVILEGED_ACTIVATION " +
                    "role is supported for skipping the security delay."
    )
    public TicketResponse activateTicket(@PathVariable long id, @RequestBody @Valid TicketActivationParams params, Authentication authentication) {
        Ticket ticket = getTicketOrThrow(id);
        boolean isPrivileged = CertificateAuthService.hasRole(authentication, Privileges.PRIVILEGED_ACTIVATION);
        if (!isPrivileged) {
            if (!AccountService.checkAccess(authentication, ticket.getHolderAccount().getId())) {
                throw new AccessDeniedException("Unprivileged caller attempted to activate a ticket that isn't theirs!");
            }
        }
        return createTicketResponse(activateTicketImpl(
                ticket,
                params,
                isPrivileged,
                CertificateAuthService.hasRole(authentication, Privileges.LWT_DEVICE) ? TicketActivationSource.LWT_VALIDATOR : TicketActivationSource.USER
        ), isPrivileged, true);
    }

    private Ticket activateTicketImpl(Ticket ticket, TicketActivationParams params, boolean isPrivileged, TicketActivationSource source) {
        if (params.clientIntegrityAttested() && !isPrivileged) {
            throw new AccessDeniedException("Unprivileged caller attempted to attest for client integrity.");
        }
        ticket = activationService.activateTicket(ticket, params, isPrivileged, source);
        return ticket;
    }

    private TicketResponse createTicketResponse(Ticket ticket, boolean privileged, boolean canShowPayloads) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getProductId(),
                ticket.getHolderAccount() != null ? ticket.getHolderAccount().getId() : 0,
                ticket.getValidSince(),
                ticket.getValidUntil(),
                ticket.getValidZones(),
                ticket.getActivationToken() != null && (privileged || ticket.getPayload() == null) ? ticket.getActivationToken().getActivationToken() : null,
                ticket.getActivationRecord() != null ? ticket.getActivationRecord().getActivationTime() : null,
                ticket.getActivationRecord() != null && privileged ? ticket.getActivationRecord().getAppId() : null,
                canShowPayloads ? createTicketPayloadResponse(ticket.getPayload()) : null
        );
    }

    private TicketPayloadResponse createTicketPayloadResponse(TicketPayload payload) {
        if (payload == null) {
            return null;
        }
        return new TicketPayloadResponse(
                payload.getEtd(),
                payload.getDerivedTotpSeed()
        );
    }
}
