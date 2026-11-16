package cz.spojenka.lwt.ticketingserver.controllers;

import cz.spojenka.lwt.ticketing.api.TelemetryEventBatch;
import cz.spojenka.lwt.ticketing.api.TelemetryEventClass;
import cz.spojenka.lwt.ticketing.api.TelemetryEventPush;
import cz.spojenka.lwt.ticketingserver.api.AccessDeniedException;
import cz.spojenka.lwt.ticketingserver.model.Privileges;
import cz.spojenka.lwt.ticketingserver.services.CertificateAuthService;
import cz.spojenka.lwt.ticketingserver.services.TelemetryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@RestController
@Tag(name = "Telemetry", description = "General-purpose telemetry event ingestion API")
public class TelemetryController {

    private static final Map<String, TelemetryEventClass> PRIVILEGE_TO_EVENT_CLASS = Map.of(
            Privileges.LWT_DEVICE, TelemetryEventClass.LWT,
            Privileges.TICKET_INSPECTOR, TelemetryEventClass.INSPECTION
    );

    private TelemetryService service;

    public TelemetryController(TelemetryService service) {
        this.service = service;
    }

    @PostMapping("/telemetry/events")
    public void pushEvents(@RequestBody TelemetryEventBatch events, Authentication authentication) {
        X509Certificate cert = CertificateAuthService.getCertificate(authentication);
        if (cert == null) {
            throw new AccessDeniedException("Client certificate is required to push telemetry events");
        }
        String certifiedSenderId = CertificateAuthService.extractCommonName(cert.getSubjectX500Principal());
        if (certifiedSenderId == null || certifiedSenderId.isBlank()) {
            throw new AccessDeniedException("Client certificate must have a valid subject CN");
        }

        Set<TelemetryEventClass> permittedEventClasses = new HashSet<>();
        for (var perm : PRIVILEGE_TO_EVENT_CLASS.entrySet()) {
            if (CertificateAuthService.hasRole(authentication, perm.getKey())) {
                permittedEventClasses.add(perm.getValue());
            }
        }

        for (TelemetryEventPush event : events.events()) {
            if (!permittedEventClasses.contains(event.eventClass())) {
                throw new AccessDeniedException("Client does not have permission to create events of class " + event.eventClass());
            }
        }

        events = ensureCertifiedSenderId(events, certifiedSenderId);

        service.ingestEvents(events);
    }

    private TelemetryEventBatch ensureCertifiedSenderId(TelemetryEventBatch events, String certifiedSenderId) {
        String senderIdSuffix = "@" + certifiedSenderId;

        return new TelemetryEventBatch(
                events
                        .events()
                        .stream()
                        .map(e -> {
                            if (Objects.equals(e.senderId(), certifiedSenderId)) {
                                return e;
                            }
                            if (e.senderId() == null || e.senderId().isBlank()) {
                                return e.withSenderId(certifiedSenderId);
                            }
                            if (!e.senderId().endsWith(senderIdSuffix)) {
                                return e.withSenderId(e.senderId() + senderIdSuffix);
                            }
                            return e;
                        })
                        .toList(),
                events.currentLocalTimestamp()
        );
    }
}
