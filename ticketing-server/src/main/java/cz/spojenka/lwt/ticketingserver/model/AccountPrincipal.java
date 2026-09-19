package cz.spojenka.lwt.ticketingserver.model;

import cz.spojenka.lwt.ticketingserver.api.NotFoundException;
import org.jspecify.annotations.Nullable;
import org.springframework.security.oauth2.jwt.Jwt;

public class AccountPrincipal extends Jwt {

    private final Account account;

    public AccountPrincipal(Jwt jwt, Account account) {
        super(jwt.getTokenValue(), jwt.getIssuedAt(), jwt.getExpiresAt(), jwt.getHeaders(), jwt.getClaims());
        this.account = account;
    }

    public boolean accountExists() {
        return account != null;
    }

    public Account requireAccount() {
        if (account == null) {
            throw new NotFoundException("Account not found for subject: " + getSubject());
        }
        return account;
    }

    public AccountPrincipal withAccount(@Nullable Account account) {
        return new AccountPrincipal(this, account);
    }
}
