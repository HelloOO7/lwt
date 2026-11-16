package cz.spojenka.lwt.ticketingserver.services;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;

import java.security.cert.X509Certificate;

public class CertificateGrantedAuthority implements GrantedAuthority {

    private final String authority;
    private final X509Certificate certificate;

    public CertificateGrantedAuthority(String authority, X509Certificate certificate) {
        this.authority = authority;
        this.certificate = certificate;
    }

    @Override
    public @Nullable String getAuthority() {
        return authority;
    }

    public X509Certificate getCertificate() {
        return certificate;
    }
}
