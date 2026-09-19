package cz.spojenka.lwt.ticketingserver.services;

import cz.spojenka.lwt.ticketingserver.api.AccessDeniedException;
import cz.spojenka.lwt.ticketingserver.api.NotFoundException;
import cz.spojenka.lwt.ticketingserver.model.Account;
import cz.spojenka.lwt.ticketingserver.model.AccountRepository;
import cz.spojenka.lwt.ticketingserver.model.SecureToken;
import jakarta.transaction.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.util.Arrays;

@Service
public class AccountService {

    private final AccountRepository repository;
    private final TicketSigningService cicoTokenSigner;

    public AccountService(AccountRepository repository, TicketSigningService cicoTokenSigner) {
        this.repository = repository;
        this.cicoTokenSigner = cicoTokenSigner;
    }

    public Account findByJwt(Jwt jwt) {
        String issuer = jwt.getIssuer().toString();
        String subject = jwt.getSubject();
        return repository.findByIdentity(issuer, subject);
    }

    private Account newAccount() {
        Account account = new Account();
        account = repository.save(account);
        // must save to generate ID
        account.setCicoToken(generateCicoToken(account));
        account = repository.save(account);
        return account;
    }

    private byte[] generateCicoToken(Account account) {
        byte[] salt = RandomGenerator.bytes(16);

        return SecureToken.create(
                ByteBuffer.allocate(Long.BYTES + salt.length)
                        .putLong(account.getId())
                        .put(salt)
                        .array(),
                0,
                cicoTokenSigner::signActivationToken
        );
    }

    @Transactional
    public Account findOrCreateByJwt(Jwt jwt) {
        String issuer = jwt.getIssuer().toString();
        String subject = jwt.getSubject();
        Account account = repository.findByIdentity(issuer, subject);
        if (account == null) {
            account = newAccount();
            account.addIdentity(issuer, subject);
            account = repository.save(account);
        }
        return account;
    }

    public Account resetCicoToken(Account account) {
        account.setCicoToken(generateCicoToken(account));
        return repository.save(account);
    }

    public Account getAccountByCicoToken(byte[] cicoToken) {
        ByteBuffer data = ByteBuffer.wrap(SecureToken.parseAndVerify(cicoToken, cicoTokenSigner::verifySignature));
        long accountId = data.getLong();
        Account account = findByIdOrThrow(accountId);
        if (Arrays.equals(account.getCicoToken(), cicoToken)) {
            return account;
        } else {
            throw new AccessDeniedException("CICO token for account " + accountId + " does not match (probably was reset)");
        }
    }

    public Account findByIdOrThrow(long accountId) {
        return repository.findById(accountId).orElseThrow(() -> new NotFoundException("Account not found for ID " + accountId));
    }
}
