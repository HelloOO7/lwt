package cz.spojenka.lwt.ticketingserver.services;

import org.bouncycastle.asn1.x500.AttributeTypeAndValue;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.AuthenticationUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.stereotype.Service;

import javax.security.auth.x500.X500Principal;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Service
public class CertificateAuthService implements AuthenticationUserDetailsService<PreAuthenticatedAuthenticationToken> {

    @Override
    public @NonNull UserDetails loadUserDetails(PreAuthenticatedAuthenticationToken token) {
        X509Certificate cert = (X509Certificate) token.getCredentials();

        Collection<GrantedAuthority> authorities = List.of();
        X500Principal principal = null;

        if (cert != null) {
            principal = cert.getSubjectX500Principal();
            if (principal != null) {
                authorities = extractAuthorities(cert, principal);
            }
        }

        return User.builder()
                .username(principal != null ? extractCommonName(principal) : "")
                .password("")
                .authorities(authorities)
                .build();
    }

    public static String extractCommonName(X500Principal principal) {
        X500Name subject = X500Name.getInstance(principal.getEncoded());
        RDN[] cnRdns = subject.getRDNs(BCStyle.CN);
        if (cnRdns.length > 0) {
            return cnRdns[0].getFirst().getValue().toString();
        }
        return "";
    }

    private Collection<GrantedAuthority> extractAuthorities(X509Certificate certificate, X500Principal subjectPrincipal) {
        X500Name subject = X500Name.getInstance(subjectPrincipal.getEncoded());

        RDN[] rdns = subject.getRDNs(BCStyle.ROLE);

        List<GrantedAuthority> authorities = new ArrayList<>();

        for (RDN rdn : rdns) {
            for (AttributeTypeAndValue roleValue : rdn.getTypesAndValues()) {
                authorities.add(new CertificateGrantedAuthority(roleValue.getValue().toString(), certificate));
            }
        }

        return authorities;
    }

    public static X509Certificate getCertificate(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (authority instanceof CertificateGrantedAuthority certAuthority) {
                return certAuthority.getCertificate();
            }
        }
        return null;
    }

    public static boolean hasRole(@Nullable Authentication authentication, String role) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> Objects.equals(authority.getAuthority(), role));
    }
}
