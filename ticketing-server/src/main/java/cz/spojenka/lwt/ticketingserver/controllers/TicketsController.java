package cz.spojenka.lwt.ticketingserver.controllers;

import cz.spojenka.lwt.ticketing.api.ProductResponse;
import cz.spojenka.lwt.ticketing.api.TicketActivationParams;
import cz.spojenka.lwt.ticketing.api.TicketResponse;
import cz.spojenka.lwt.ticketingserver.api.AccessDeniedException;
import cz.spojenka.lwt.ticketingserver.model.*;
import cz.spojenka.lwt.ticketingserver.services.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import shaded.org.apache.commons.codec.binary.Hex;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@RestController
@Tag(name = "Tickets", description = "Ticket management API")
public class TicketsController {

    private final TicketIssuanceService ticketService;
    private final ProductRepository productRepository;
    private final ProductImportService productImportService;
    private final TicketActivationService activationService;
    private final AccountService accountService;
    private final TicketFacade facade;
    private final TelemetryService telemetry;

    public TicketsController(TicketIssuanceService ticketService, ProductRepository productRepository, ProductImportService productImportService, TicketActivationService activationService, AccountService accountService, TicketFacade facade, TelemetryService telemetry) {
        this.ticketService = ticketService;
        this.productRepository = productRepository;
        this.productImportService = productImportService;
        this.activationService = activationService;
        this.accountService = accountService;
        this.facade = facade;
        this.telemetry = telemetry;
    }

    @GetMapping("/tickets/available")
    @Operation(
            summary = "List of ticket types available for purchase",
            description = "Returns a list of all short-term transport products in the system that can be purchased by customers."
    )
    public ResponseEntity<List<ProductResponse>> getAvailableTickets(@RequestParam(value = "includeOutsideValidity", required = false) boolean includeOutsideValidity, WebRequest request) {
        String etag = Hex.encodeHexString(productImportService.getCurrentDataChecksum());
        if (request.checkNotModified(etag, productImportService.getCurrentDataTimestamp().toEpochMilli())) {
            return new ResponseEntity<>(HttpStatus.NOT_MODIFIED);
        }

        List<Product> data;
        if (includeOutsideValidity) {
            data = productRepository.findAll();
        } else {
            data = productRepository.findValidAt(LocalDate.now());
        }
        List<ProductResponse> result = data.stream().map(facade::exportProduct).toList();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ETAG, etag);

        return new ResponseEntity<>(result, headers, HttpStatus.OK);
    }

    @PostMapping("/tickets/new")
    @Secured(Privileges.TICKET_ISSUER)
    @Operation(
            summary = "Issue a new ticket",
            description = "Create a new ticket for the specified product and holder account. Requires TICKET_ISSUER role.",
            security = {@SecurityRequirement(name = "certificate", scopes = {Privileges.TICKET_ISSUER})}
    )
    public TicketResponse issueNewTicket(@RequestParam("productId") int productId, @RequestParam("holderAccountId") long holderAccountId) {
        Ticket ticket = ticketService.issueTicket(accountService.findByIdOrThrow(holderAccountId), ticketService.findProductByIdOrThrow(productId));
        telemetry.ingestLocalEvent(ServerTelemetryEvent.ISSUED_TICKET, ticket.getId(), null);
        return facade.exportTicket(ticket, true, true);
    }

    @GetMapping("/tickets/{id}")
    @Operation(
            summary = "Get ticket by ID",
            description = "Returns ticket data for the specified ticket ID. If the caller is not privileged, tickets can only be "
                    + "accessed by their owners. To return payloads for unprivileged callers, the appId must be provided and match the ticket's activation record. "
                    + "The activationAppId in the response is never returned to unprivileged users."
    )
    public TicketResponse getTicketById(@PathVariable long id, @RequestParam(value = "appId", required = false) String appId, Authentication authentication) {
        Ticket ticket = ticketService.findTicketByIdOrThrow(id);
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
        return facade.exportTicket(ticket, privileged, canShowPayloads);
    }

    @PostMapping("/tickets/{id}/activate")
    @Operation(
            summary = "Activate a ticket",
            description = "Activate a ticket and return ticket data with a validation token. The PRIVILEGED_ACTIVATION " +
                    "role is supported for skipping the security delay."
    )
    public TicketResponse activateTicket(@PathVariable long id, @RequestBody @Valid TicketActivationParams params, Authentication authentication) {
        Ticket ticket = ticketService.findTicketByIdOrThrow(id);
        boolean isPrivileged = CertificateAuthService.hasRole(authentication, Privileges.PRIVILEGED_ACTIVATION);
        if (!isPrivileged) {
            if (!AccountService.checkAccess(authentication, ticket.getHolderAccount().getId())) {
                throw new AccessDeniedException("Unprivileged caller attempted to activate a ticket that isn't theirs!");
            }
        }
        return facade.exportTicket(activateTicketImpl(
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
}
