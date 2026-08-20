package cz.spojenka.lwt.ticketingserver;

import cz.spojenka.lwt.ticketingserver.services.CertificateAuthService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity(jsr250Enabled = true, prePostEnabled = true, securedEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, CertificateAuthService certificateAuthService) {
        http
                .csrf(csrf -> csrf.disable())
                .x509(x509 -> x509.authenticationUserDetailsService(certificateAuthService))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll());

        return http.build();
    }
}
