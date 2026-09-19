package cz.spojenka.lwt.ticketingserver.controllers;

import cz.spojenka.lwt.ticketing.api.AccountResponse;
import cz.spojenka.lwt.ticketing.api.ClientOAuthConfig;
import cz.spojenka.lwt.ticketingserver.model.Account;
import cz.spojenka.lwt.ticketingserver.model.AccountPrincipal;
import cz.spojenka.lwt.ticketingserver.services.AccountService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class AccountsController {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String oidcIssuerUri;

    private final AccountService accountService;

    public AccountsController(AccountService accountService) {
        this.accountService = accountService;
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

    private AccountResponse createAccountResponse(AccountPrincipal account) {
        return new AccountResponse(account.requireAccount().getId(), account.getClaimAsString("email"), account.requireAccount().getCicoToken());
    }
}
