package cz.spojenka.lwt.ticketingserver.services;

import cz.spojenka.lwt.ticketingserver.model.Account;
import cz.spojenka.lwt.ticketingserver.model.AccountPrincipal;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class AccountJwtAuthConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final AccountService accountService;

    public AccountJwtAuthConverter(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt source) {
        Account account = accountService.findByJwt(source);

        return new JwtAuthenticationToken(source)
                .toBuilder()
                .principal(new AccountPrincipal(source, account))
                .build();
    }
}
