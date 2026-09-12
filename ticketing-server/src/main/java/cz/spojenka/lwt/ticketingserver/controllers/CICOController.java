package cz.spojenka.lwt.ticketingserver.controllers;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochRandomGenerator;
import cz.spojenka.lwt.ticketingserver.api.CICOEventBatch;
import cz.spojenka.lwt.ticketingserver.api.CICOEventPush;
import cz.spojenka.lwt.ticketingserver.api.CheckInRequest;
import cz.spojenka.lwt.ticketingserver.api.CheckInResponse;
import cz.spojenka.lwt.ticketingserver.services.CICOIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@RestController
public class CICOController {

    private final CICOIngestionService ingestionService;
    private final TimeBasedEpochRandomGenerator sessionIdGenerator = Generators.timeBasedEpochRandomGenerator();

    public CICOController(CICOIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/cico/check-in")
    @Secured("LWT_DEVICE")
    @Operation(
            summary = "Validate and get parameters for check-in",
            description = "Verify that a check-in token is legitimate and return account and session information.",
            security = {@SecurityRequirement(name = "certificate", scopes = {"LWT_DEVICE"})}
    )
    public CheckInResponse checkIn(@RequestBody CheckInRequest checkInRequest) {
        // currently accounts are not implemented, so let everything pass
        return new CheckInResponse(1, sessionIdGenerator.generate());
    }

    @PostMapping("/cico/events")
    @Secured("LWT_DEVICE")
    @Operation(
            summary = "CICO event ingestion endpoint",
            description = "Authorized devices use this endpoint to upload CICO events when they have "
                    + "available resources and an internet connection. It requires a certificate with the role LWT_DEVICE.",
            security = {@SecurityRequirement(name = "certificate", scopes = {"LWT_DEVICE"})}
    )
    public void pushEvents(@RequestBody CICOEventBatch events) {
        ZonedDateTime clientTime = ZonedDateTime.now();
        List<CICOEventPush> absoluteEvents = new ArrayList<>(events.events().size());
        for (CICOEventPush event : events.events()) {
            if (event.absoluteTimestamp() != null) {
                absoluteEvents.add(event);
            } else {
                absoluteEvents.add(event.withAbsoluteTimestamp(
                        clientTime.plus(Duration.ofMillis(event.localTimestamp() - events.currentLocalTimestamp())).toOffsetDateTime()
                ));
            }
        }
        ingestionService.ingestEvents(absoluteEvents);
    }
}
