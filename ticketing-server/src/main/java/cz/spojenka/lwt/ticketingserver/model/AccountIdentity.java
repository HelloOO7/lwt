package cz.spojenka.lwt.ticketingserver.model;

import jakarta.persistence.*;

@Entity
@Table(
        uniqueConstraints = @UniqueConstraint(
                name = "uk_identity_issuer_subject",
                columnNames = {"issuer", "subject"}
        )
)
public class AccountIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Account account;

    private String issuer;
    private String subject;

    AccountIdentity() {

    }

    public AccountIdentity(Account account, String issuer, String subject) {
        this.account = account;
        this.issuer = issuer;
        this.subject = subject;
    }

    public long getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public String getIssuer() {
        return issuer;
    }

    public String getSubject() {
        return subject;
    }
}
