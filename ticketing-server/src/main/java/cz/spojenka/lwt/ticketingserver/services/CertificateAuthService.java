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
                authorities = extractAuthorities(principal);
            }
        }

        return User.builder()
                .username(principal != null ? principal.getName() : "default")
                .password("")
                .authorities(authorities)
                .build();
    }

    private Collection<GrantedAuthority> extractAuthorities(X500Principal subjectPrincipal) {
        X500Name subject = X500Name.getInstance(subjectPrincipal.getEncoded());

        RDN[] rdns = subject.getRDNs(BCStyle.ROLE);

        List<GrantedAuthority> authorities = new ArrayList<>();

        for (RDN rdn : rdns) {
            for (AttributeTypeAndValue roleValue : rdn.getTypesAndValues()) {
                authorities.add(new SimpleGrantedAuthority(roleValue.getValue().toString()));
            }
        }

        return authorities;
    }

    public static boolean hasRole(@Nullable Authentication authentication, String role) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> Objects.equals(authority.getAuthority(), role));
    }
}
