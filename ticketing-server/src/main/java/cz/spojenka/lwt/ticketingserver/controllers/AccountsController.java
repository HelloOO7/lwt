package cz.spojenka.lwt.ticketingserver.controllers;

import cz.spojenka.lwt.ticketing.api.*;
import cz.spojenka.lwt.ticketingserver.model.*;
import cz.spojenka.lwt.ticketingserver.services.AccountService;
import cz.spojenka.lwt.ticketingserver.services.CICOFacade;
import cz.spojenka.lwt.ticketingserver.services.CICOSessionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "User accounts", description = "User account information and management API")
public class AccountsController {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String oidcIssuerUri;

    private final AccountService accountService;
    private final CICOSessionService cicoSessionService;
    private final CICOFacade cicoFacade;

    public AccountsController(AccountService accountService, CICOSessionService cicoSessionService, CICOFacade cicoFacade) {
        this.accountService = accountService;
        this.cicoSessionService = cicoSessionService;
        this.cicoFacade = cicoFacade;
    }

    @GetMapping("/accounts/oauth-config")
    public ClientOAuthConfig getOAuthConfig() {
        return new ClientOAuthConfig(
                oidcIssuerUri,
                List.of("openid", "email", "profile")
        );
    }

    @GetMapping("/accounts/me")
    public AccountResponse getCurrentAccount(@AuthenticationPrincipal AccountPrincipal principal) {
        Account account = principal.requireAccount();
        return new AccountResponse(account.getId(), principal.getClaimAsString("email"), account.getCicoToken());
    }

    @PostMapping("/accounts/me")
    public AccountResponse getOrCreateAccountByOAuth(
            @RequestParam(value = "createIfNew", required = false) Boolean createIfNew,
            @AuthenticationPrincipal AccountPrincipal principal
    ) {
        Account account;
        if (createIfNew != null && createIfNew) {
            account = accountService.findOrCreateByJwt(principal);
        } else {
            account = principal.requireAccount();
        }
        return createAccountResponse(principal.withAccount(account));
    }

    @PostMapping("/accounts/me/reset-cico-token")
    public AccountResponse resetCicoToken(@AuthenticationPrincipal AccountPrincipal principal) {
        return createAccountResponse(principal.withAccount(accountService.resetCicoToken(principal.requireAccount())));
    }

    @GetMapping("/accounts/me/cico-sessions")
    public PagedModel<CICOSessionInfo> getCicoSessions(
            @RequestParam(value = "includeCleared", required = false) boolean includeCleared,
            @AuthenticationPrincipal AccountPrincipal principal,
            Pageable pageable
    ) {
        return new PagedModel<>(
                cicoSessionService
                        .getSessionsForAccount(principal.requireAccount(), includeCleared, pageable)
                        .map(cicoFacade::exportSessionInfo)
        );
    }

    private AccountResponse createAccountResponse(AccountPrincipal account) {
        return new AccountResponse(account.requireAccount().getId(), account.getClaimAsString("email"), account.requireAccount().getCicoToken());
    }

    @GetMapping("/accounts/me/cico-fare-history")
    public PagedModel<CICOClearedFare> getCicoFareHistory(
            @RequestParam(value = "includeCancelled", required = false) boolean includeCancelled,
            @AuthenticationPrincipal AccountPrincipal principal,
            Pageable pageable
    ) {
        return new PagedModel<>(
                cicoSessionService.getClearingsForAccount(principal.requireAccount(), includeCancelled, pageable)
                        .map(clearing -> cicoFacade.exportClearedFare(clearing, cicoSessionService.findSessionIDsInClearing(clearing)))
        );
    }
}
