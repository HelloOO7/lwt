package cz.spojenka.lwt.ticketingserver.services;

import cz.dpp.praguepublictransport.LitackaUtils;
import cz.spojenka.datasources.enums.ArrivalOrDeparture;
import cz.spojenka.datasources.enums.LineIDRegistry;
import cz.spojenka.datasources.model.timetable.ConnectionEntity;
import cz.spojenka.datasources.model.timetable.TimetableDatabase;
import cz.spojenka.datasources.persistentids.ParameterLine;
import cz.spojenka.datasources.persistentids.PersistentIDService;
import cz.spojenka.engine.jni.*;
import cz.spojenka.lwt.ticketingserver.model.*;
import cz.spojenka.lwt.ticketingserver.model.Ticket;
import cz.spojenka.lwt.ticketingserver.util.TimetableUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CICOTariffCalculation {

    @Value("${tickets.permitted-tariffs}")
    private List<String> permittedTariffs;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TimetableDatabase tt;
    @Autowired
    private SearchLibrary tcEngine;

    private final List<CICOEvent> events;
    private final Account account;

    @Autowired
    private PersistentIDService persistentIDs;

    public CICOTariffCalculation(List<CICOEvent> events) {
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
        Journey reconstructedJourney = reconstructJourneyFromEvents();
        TariffResult tr = engineCalculate(reconstructedJourney);
        List<Ticket> tickets = ticketsFromJni(tr, reconstructedJourney);
        double price = Arrays.stream(tr.tickets).mapToDouble(t -> t.ticket.price).sum();
        return new Result(tickets, price);
    }

    private Journey reconstructJourneyFromEvents() {
        // debugging
        Journey j = new Journey();
        Trip t = new Trip();
        ConnectionEntity conn = persistentIDs.getConnectionByPersistentID("L:CISJR:100384,CN:CISJR:1059,SD:2026-10-16,FS:crz:10034,FSI:0,FT:23:10:00,TS:crz:9998,TSI:14,TT:23:52:00");
        var rs = conn.getRoute().stops();
        t.connectionId = conn.getID();
        t.from = new TripStop();
        t.from.routeIndex = 0;
        t.from.stopId = rs.at(t.from.routeIndex).getStopPost().getLongLinearID();
        t.from.time = ZonedDateTime.of(2026, 10, 16, 23, 10, 0, 0, ZoneId.systemDefault());
        t.to = new TripStop();
        t.to.routeIndex = conn.stopTimes().count() - 1;
        t.to.stopId = rs.at(t.to.routeIndex).getStopPost().getLongLinearID();
        t.to.time = ZonedDateTime.of(2026, 10, 16, 23, 52, 0, 0, ZoneId.systemDefault());
        j.trips = new Trip[]{t};
        return j;
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
        List<String> sectionDescs = new ArrayList<>();
        for (var section : journeyTicket.validAtSections) {
            for (int ti = section.from.journeyTripIndex; ti <= section.to.journeyTripIndex; ti++) {
                ConnectionEntity conn = conns.get(ti);
                int fs = ti == section.from.journeyTripIndex ? section.from.tripRouteIndex : 0;
                int ts = ti == section.to.journeyTripIndex ? section.to.tripRouteIndex : conn.stopTimes().count() - 1;

                Map<String, String> sectionRec = Map.of(
                        "CAN", persistentIDs.createPersistentConnectionID(
                                conn,
                                TimetableUtils.getStopTimeByStopTime(conn, journey.trips[ti].from, fs, ArrivalOrDeparture.DEPARTURE).toLocalDate(),
                                fs,
                                ArrivalOrDeparture.DEPARTURE,
                                fs,
                                ts
                        ),
                        "BU", ParameterLine.defaultConfig().encodePOJO(TripDescBackup.create(conn, journey.trips[ti].from, fs, ts, permittedTariffs))
                );

                sectionDescs.add(ParameterLine.withSeparator('|').encode(sectionRec));
            }
        }
        return ParameterLine.escapeAndJoin(sectionDescs, '>');
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

    public static record Result(List<Ticket> tickets, double totalPrice) {

    }
}
