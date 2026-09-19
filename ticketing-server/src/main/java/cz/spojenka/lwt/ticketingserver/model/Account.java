package cz.spojenka.lwt.ticketingserver.model;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @OneToMany(
            mappedBy = "account",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<AccountIdentity> identities = new ArrayList<>();

    private byte[] cicoToken;

    public Account() {

    }

    public long getId() {
        return id;
    }

    public AccountIdentity addIdentity(String issuer, String subject) {
        AccountIdentity identity = new AccountIdentity(this, issuer, subject);
        identities.add(identity);
        return identity;
    }

    public byte[] getCicoToken() {
        return cicoToken;
    }

    public void setCicoToken(byte[] cicoToken) {
        this.cicoToken = cicoToken;
    }
}
