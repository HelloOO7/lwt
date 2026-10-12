package cz.spojenka.lwt.ticketingserver.services;

import cz.dpp.praguepublictransport.LitackaUtils;
import cz.spojenka.datasources.enums.LineIDRegistry;
import cz.spojenka.datasources.model.timetable.ConnectionEntity;
import cz.spojenka.datasources.model.timetable.LineEntity;
import cz.spojenka.datasources.model.timetable.TimetableDatabase;
import cz.spojenka.engine.jni.*;
import cz.spojenka.lwt.ticketingserver.model.*;
import cz.spojenka.lwt.ticketingserver.model.Ticket;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CICOTariffCalculation {

    @Value("${tickets.permitted-tariffs}")
    private List<String> permittedTariffs;

    private final AccountService accountService;

    private final TimetableDatabase tt;
    private final SearchLibrary tcEngine;

    private final List<CICOEvent> events;
    private final Account account;

    public CICOTariffCalculation(AccountService accountService, TimetableDatabase tt, SearchLibrary tcEngine, List<CICOEvent> events) {
        this.accountService = accountService;
        this.tt = tt;
        this.tcEngine = tcEngine;
        this.events = events;
        if (events.isEmpty()) {
            throw new IllegalArgumentException("No events were given");
        }
        account = events.getFirst().getAccount();
        for (CICOEvent event : events) {
            if (!event.getAccount().equals(account)) {
                throw new IllegalArgumentException("All events must belong to the same account");
            }
        }
    }

    public Result calculate() {
        TariffResult tr = engineCalculate(reconstructJourneyFromEvents());
        List<Ticket> tickets = new ArrayList<>(tr.tickets.length);
        //for (Ticke)
        return null;
    }

    private Journey reconstructJourneyFromEvents() {
        return null;
    }

    private List<Ticket> ticketsFromJni(TariffResult tr, Journey srcJourney) {
        return Arrays.stream(tr.tickets)
                .map(tik -> ticketFromJni(tik, srcJourney))
                .toList();
    }

    private Ticket ticketFromJni(JourneyTicket journeyTicket, Journey srcJourney) {
        cz.spojenka.engine.jni.Ticket jniTicket = journeyTicket.ticket;
        Ticket ticket = new Ticket(Integer.parseInt(jniTicket.optionUid), account);
        if (jniTicket instanceof TicketZoned zoned) {
            ticket.activate(
                    jniTicket.validFrom.toOffsetDateTime(),
                    LitackaUtils.createCommaSeparatedList(List.of(zoned.logicalZones)),
                    new TicketActivationRecord(
                            "cico-acc" + account.getId(),
                            jniTicket.validFrom.toOffsetDateTime(),
                            TicketActivationSource.CICO_CLEARING,
                            createCicoTicketTravelRecord(srcJourney, journeyTicket)
                    )
            );
        }
        return ticket;
    }

    private String createCicoTicketTravelRecord(Journey journey, JourneyTicket journeyTicket) {
        List<ConnectionEntity> conns = new ArrayList<>();
        for (Trip trip : journey.trips) {
            conns.add(tt.getConnectionById(trip.connectionId));
        }
        StringBuilder sb = new StringBuilder();
        for (var section : journeyTicket.validAtSections) {
            if (!sb.isEmpty()) {
                sb.append(">");
            }
            for (int ti = section.from.journeyTripIndex; ti <= section.to.journeyTripIndex; ti++) {
                int fs = ti == section.from.journeyTripIndex ? section.from.tripRouteIndex : 0;
                int ts = ti == section.to.journeyTripIndex ? section.to.tripRouteIndex : conns.get(ti).stopTimes().count() - 1;
                // todo
            }
        }
        return sb.toString();
    }

    private TariffResult engineCalculate(Journey journey) {
        return tcEngine.calculateJourneyTariff(
                journey,
                new NegotiationResolution[0],
                createPassengerInfo(),
                permittedTariffs.toArray(String[]::new)
        );
    }

    private Passenger createPassengerInfo() {
        Passenger passenger = new Passenger();
        passenger.birthDate = LocalDate.of(2000, 1, 1); // placeholder - will be filled from profile
        List<Pass> accountPasses = account.getPasses();
        passenger.prepaidCoupons = accountPasses
                .stream()
                .map(pass -> {
                    PrepaidCouponZoned coupon = new PrepaidCouponZoned();
                    coupon.service = "PID"; // placeholder - will be filled from product ID of pass
                    coupon.validFrom = pass.getValidSince().toZonedDateTime();
                    coupon.validTo = pass.getValidUntil().toZonedDateTime();
                    coupon.zones = LitackaUtils.parseCommaSeparatedList(pass.getValidZones()).toArray(String[]::new);
                    passenger.prepaidCoupons[accountPasses.indexOf(pass)] = coupon;
                    return coupon;
                })
                .toArray(PrepaidCouponZoned[]::new);
        return passenger;
    }

    public static record Result(List<Ticket> tickets, float totalPrice) {

    }
}
