package cz.spojenka.lwt.ticketingserver.controllers;

import cz.spojenka.lwt.ticketing.api.*;
import cz.spojenka.lwt.ticketingserver.api.AccessDeniedException;
import cz.spojenka.lwt.ticketingserver.api.NotFoundException;
import cz.spojenka.lwt.ticketingserver.model.*;
import cz.spojenka.lwt.ticketingserver.services.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.annotation.Secured;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "CICO events and tariff", description = "CICO event ingestion and client tariff calculation API")
public class CICOController {

    private final AccountService accountService;
    private final CICORepository repository;
    private final CICOIngestionService ingestionService;
    private final CICOSessionService sessionService;
    private final TariffCalculationService tariffService;
    private final CICOFacade facade;

    @Value("${tickets.cico.allow-self-checkout:false}")
    private boolean allowSelfCheckout;

    public CICOController(AccountService accountService, CICORepository repository, CICOIngestionService ingestionService, CICOSessionService sessionService, TariffCalculationService tariffService, CICOFacade facade) {
        this.accountService = accountService;
        this.repository = repository;
        this.ingestionService = ingestionService;
        this.sessionService = sessionService;
        this.tariffService = tariffService;
        this.facade = facade;
    }

    @PostMapping("/cico/check-in")
    @Secured(Privileges.LWT_DEVICE)
    @Operation(
            summary = "Validate and get parameters for check-in",
            description = "Verify that a check-in token is legitimate and return account and session information.",
            security = {@SecurityRequirement(name = "certificate", scopes = {Privileges.LWT_DEVICE})}
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
        boolean privileged = CertificateAuthService.hasRole(authentication, Privileges.LWT_DEVICE);
        Account account = accountService.getAccountByCicoToken(checkOutRequest.checkInToken());
        if (!privileged) {
            requireAccountMatch(account, authentication);
        }
        CICOEvent lastEvent = repository.findNewestEventInSession(checkOutRequest.sessionId());
        if (lastEvent == null) {
            throw new NotFoundException("Session not found: " + checkOutRequest.sessionId());
        }
        if (lastEvent.getAccount().getId() != account.getId()) {
            throw new AccessDeniedException("CICO token and session do not match (accounts are different)");
        }
        if (lastEvent.getEventType() == CICOEventType.BE_OUT || lastEvent.getEventType() == CICOEventType.CHECK_OUT) {
            // already checked out
            return;
        }
        ingestionService.ingestSelfCheckout(account, checkOutRequest.sessionId(), lastEvent);
    }

    private void requireAccountMatch(Account account, Authentication authentication) {
        if (authentication.getPrincipal() instanceof AccountPrincipal loggedAccount) {
            if (account.getId() != loggedAccount.requireAccount().getId()) {
                throw new AccessDeniedException("Account " + loggedAccount.getId() + " is not authorized to perform operations on behalf of another.");
            }
        } else {
            throw new AccessDeniedException("Not logged in");
        }
    }

    @PostMapping("/cico/events")
    @Secured(Privileges.LWT_DEVICE)
    @Operation(
            summary = "CICO event ingestion endpoint",
            description = "Authorized devices use this endpoint to upload CICO events when they have "
                    + "available resources and an internet connection. It requires a certificate with the role LWT_DEVICE.",
            security = {@SecurityRequirement(name = "certificate", scopes = {Privileges.LWT_DEVICE})}
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

    @GetMapping("/cico/evaluate-session")
    @Operation(
            summary = "Evaluate a CICO session and calculate tariff",
            description = "Given a session ID, the server will evaluate it into a journey in its known timetable (if possible) and "
                    + "calculate an intermediate tariff for it. This should be used by clients to check the current cost of a journey "
                    + "before clearing. The timeframe where this method can be used is restricted, as after some time, the used trips will no longer "
                    + "be present in the timetable and, as such, tariff calculation will not be possible. These results are informative only, the final "
                    + "price will only be known after clearing at the end of day. Caller must be either the account holder for the session or have the "
                    + "TICKET_ISSUER privilege."
    )
    public CICOClearedFare evaluateCICOSession(@RequestParam("sessionId") UUID sessionId, Authentication authentication) {
        List<CICOEvent> events = repository.findAllInSession(sessionId);
        if (events.isEmpty()) {
            throw new NotFoundException("Session not found: " + sessionId);
        }

        boolean privileged = CertificateAuthService.hasRole(authentication, Privileges.TICKET_ISSUER);
        Account sessionAccount = events.getFirst().getAccount();
        if (!privileged) {
            requireAccountMatch(sessionAccount, authentication);
        }

        var fare = tariffService.calculateCICOTariff(events);

        return facade.exportClearedFare(new CICOClearingRecord(sessionAccount, OffsetDateTime.now(), fare.tickets(), fare.totalPrice()), List.of(sessionId));
    }

    // this is a GET only for debugging purposes because firefox does not send mTLS certificate in POST requests
    // from dev tools
    @GetMapping("/cico/run-clearing")
    @Operation(
            summary = "Run clearing of CICO sessions",
            description = "This endpoint can be used by a privileged ticket issuer to run clearing of CICO sessions " +
                    "for one or all accounts.",
            security = {@SecurityRequirement(name = "certificate", scopes = {Privileges.TICKET_ISSUER})}
    )
    @Secured(Privileges.TICKET_ISSUER)
    public ClearingStats runClearing(@RequestParam(value = "accountId", required = false) Long accountId) {
        if (accountId != null) {
            return sessionService.runClearingForAccount(accountService.findByIdOrThrow(accountId));
        } else {
            return sessionService.runGlobalClearing();
        }
    }

    // dtto
    @GetMapping("/cico/undo-clearing")
    @Operation(
            summary = "Undo a specific clearing operation",
            description = "This endpoint can be used by a privileged ticket issuer to undo a specific clearing operation, " +
                    "which will mark all events cleared by the specified clearing record as uncleared and cancel any issued tickets. " +
                    "This operation can only be performed while the timetable is new enough that the cleared tariff can be calculated again. " +
                    "If this condition is not met, the operation will fail with an error. Caller must have the TICKET_ISSUER privilege.",
            security = {@SecurityRequirement(name = "certificate", scopes = {Privileges.TICKET_ISSUER})}
    )
    @Secured(Privileges.TICKET_ISSUER)
    public void undoClearing(@RequestParam("clearingId") long clearingId) {
        sessionService.undoClearing(sessionService.findClearingByIdOrThrow(clearingId));
    }
}
