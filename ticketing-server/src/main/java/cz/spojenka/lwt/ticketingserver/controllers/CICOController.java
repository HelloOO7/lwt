package cz.spojenka.lwt.ticketingserver.controllers;

import cz.spojenka.lwt.ticketing.api.*;
import cz.spojenka.lwt.ticketingserver.api.AccessDeniedException;
import cz.spojenka.lwt.ticketingserver.api.NotFoundException;
import cz.spojenka.lwt.ticketingserver.model.Account;
import cz.spojenka.lwt.ticketingserver.model.AccountPrincipal;
import cz.spojenka.lwt.ticketingserver.model.CICOEvent;
import cz.spojenka.lwt.ticketingserver.model.CICORepository;
import cz.spojenka.lwt.ticketingserver.services.AccountService;
import cz.spojenka.lwt.ticketingserver.services.CICOIngestionService;
import cz.spojenka.lwt.ticketingserver.services.CertificateAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
public class CICOController {

    private final AccountService accountService;
    private final CICORepository repository;
    private final CICOIngestionService ingestionService;

    @Value("${tickets.allow-self-checkout:false}")
    private boolean allowSelfCheckout;

    public CICOController(AccountService accountService, CICORepository repository, CICOIngestionService ingestionService) {
        this.accountService = accountService;
        this.repository = repository;
        this.ingestionService = ingestionService;
    }

    @PostMapping("/cico/check-in")
    @Secured("LWT_DEVICE")
    @Operation(
            summary = "Validate and get parameters for check-in",
            description = "Verify that a check-in token is legitimate and return account and session information.",
            security = {@SecurityRequirement(name = "certificate", scopes = {"LWT_DEVICE"})}
    )
    public CheckInResponse checkIn(@RequestBody CheckInRequest checkInRequest) {
        Account cicoAccount = accountService.getAccountByCicoToken(checkInRequest.checkInToken());
        return new CheckInResponse(cicoAccount.getId(), ingestionService.generateSessionUUID());
    }

    @PostMapping("/cico/self-check-out")
    @Operation(
            summary = "User-initiated online check-out",
            description = "A user can manually check out if they are not in range of a LWT device. This is done automatically "
                    + "for clients which support be-out presence tracking, but unsupported clients can do it over internet. "
                    + "Self check-out usually has no effect unless there is an active ticket issued to the client, in which case "
                    + "the ticket is cancelled (online) and the session is closed. However, CICO tickets are issued and checked offline, "
                    + "so unauthorized use after check-out is possible, though detectable (if ticket inspection takes place). "
                    + "As self-checkout weakens the security of the system, it is opt-in, configurable by the operator."
    )
    public void selfCheckOut(@RequestBody CheckOutRequest checkOutRequest, Authentication authentication) {
        if (!allowSelfCheckout) {
            throw new AccessDeniedException("Self check-out is disabled");
        }
        boolean privileged = CertificateAuthService.hasRole(authentication, "LWT_DEVICE");
        Account account = accountService.getAccountByCicoToken(checkOutRequest.checkInToken());
        if (!privileged) {
            if (authentication.getPrincipal() instanceof AccountPrincipal loggedAccount) {
                if (account.getId() != loggedAccount.requireAccount().getId()) {
                    throw new AccessDeniedException("Attempt to check out wrong account");
                }
            } else {
                throw new AccessDeniedException("Not logged in");
            }
        }
        CICOEvent lastEvent = repository.findNewestEventInSession(checkOutRequest.sessionId());
        if (lastEvent == null) {
            throw new NotFoundException("Session not found: " + checkOutRequest.sessionId());
        }
        if (lastEvent.getAccount().getId() != account.getId()) {
            throw new AccessDeniedException("CICO token and session do not match (accounts are different)");
        }
        ingestionService.ingestSelfCheckout(account, checkOutRequest.sessionId(), lastEvent);
    }

    @PostMapping("/cico/events")
    @Secured("LWT_DEVICE")
    @Operation(
            summary = "CICO event ingestion endpoint",
            description = "Authorized devices use this endpoint to upload CICO events when they have "
                    + "available resources and an internet connection. It requires a certificate with the role LWT_DEVICE.",
            security = {@SecurityRequirement(name = "certificate", scopes = {"LWT_DEVICE"})}
    )
    public void pushEvents(@RequestBody CICOEventBatch events) {
        ZonedDateTime clientTime = ZonedDateTime.now();
        List<CICOEventPush> absoluteEvents = new ArrayList<>(events.events().size());
        for (CICOEventPush event : events.events()) {
            if (event.absoluteTimestamp() != null) {
                absoluteEvents.add(event);
            } else {
                absoluteEvents.add(event.withAbsoluteTimestamp(
                        clientTime.plus(Duration.ofMillis(event.localTimestamp() - events.currentLocalTimestamp())).toOffsetDateTime()
                ));
            }
        }
        ingestionService.ingestEvents(absoluteEvents);
    }
}
