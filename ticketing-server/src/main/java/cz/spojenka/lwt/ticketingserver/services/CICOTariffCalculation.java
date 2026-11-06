package cz.spojenka.lwt.ticketingserver.services;

import com.carrotsearch.hppc.IntHashSet;
import com.carrotsearch.hppc.IntSet;
import com.carrotsearch.hppc.ObjectIntHashMap;
import com.carrotsearch.hppc.ObjectIntMap;
import cz.dpp.praguepublictransport.LitackaUtils;
import cz.spojenka.datasources.enums.ArrivalOrDeparture;
import cz.spojenka.datasources.enums.LineIDRegistry;
import cz.spojenka.datasources.model.timetable.ConnectionEntity;
import cz.spojenka.datasources.model.timetable.TimetableDatabase;
import cz.spojenka.datasources.persistentids.ParameterLine;
import cz.spojenka.datasources.persistentids.PersistentIDService;
import cz.spojenka.engine.jni.*;
import cz.spojenka.lwt.ticketing.api.CICOEventType;
import cz.spojenka.lwt.ticketingserver.model.*;
import cz.spojenka.lwt.ticketingserver.model.Ticket;
import cz.spojenka.lwt.ticketingserver.util.TimetableUtils;
import cz.spojenka.lwt.ticketingserver.util.TripAtDay;
import org.jgrapht.Graph;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.AsSubgraph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DirectedAcyclicGraph;
import org.jgrapht.graph.builder.GraphBuilder;
import org.jgrapht.traverse.TopologicalOrderIterator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.*;

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
    @Autowired
    private TripLookupService tripLookup;

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
        List<CICOEvent> sortedEvents = sortEventsTopologically(events);
        List<TripEventCollection> tripEvents = splitEventsByTrips(sortedEvents);
        for (TripEventCollection ec : tripEvents) {
            cleanUpTripEvents(ec, tripEvents);
        }
        tripEvents = tripEvents
                .stream()
                .filter(ec -> !ec.events.isEmpty())
                .sorted(Comparator.comparing(TripEventCollection::getFirstEventTime))
                .toList();

        Journey j = new Journey();
        j.trips = tripEvents.stream().map(this::tripFromFinalEventCollection).toArray(Trip[]::new);
        return j;
    }

    private void cleanUpTripEvents(TripEventCollection ec, List<TripEventCollection> allCollections) {
        removeObsoleteBeouts(ec);
        removeSingletons(ec);
        trimOverlappingTrips(ec, allCollections);
        removeSingletons(ec);
    }

    private void removeObsoleteBeouts(TripEventCollection ec) {
        Set<TripEvent> toRemove = new HashSet<>();
        // all be-out events that are not the last event in the sequence are superseded and should
        // be ignored
        for (int i = ec.events().size() - 1; i >= 0; i--) {
            TripEvent e = ec.events().get(i);
            if (e.event().getEventType() == CICOEventType.BE_OUT) {
                toRemove.add(e);
            }
        }
        ec.events().removeAll(toRemove);
    }

    private void trimOverlappingTrips(TripEventCollection ec, List<TripEventCollection> allCollections) {
        for (TripEventCollection other : allCollections) {
            if (other.isFirstTimeAfter(ec)) {
                OffsetDateTime firstTime = other.getFirstEventTime();
                ec.events().removeIf(e -> e.event().getEventTime().isAfter(firstTime));
            }
        }
    }

    private void removeSingletons(TripEventCollection ec) {
        if (!ec.events().isEmpty() && isSingletonTrip(ec)) {
            ec.events().clear();
        }
    }

    private boolean isSingletonTrip(TripEventCollection ec) {
        IntSet stopIndexes = new IntHashSet();
        for (TripEvent e : ec.events()) {
            stopIndexes.add(getClosestStopIndex(e));
            if (stopIndexes.size() > 1) {
                return false;
            }
        }
        return true;
    }

    private int getClosestStopIndex(TripEvent event) {
        int i = event.trip().stopIndex();
        if (i > 0 && event.lwtInfo().locationStateCode == LwtTicketMetadata.LocationStateCode.AFTER_STOP) {
            i--;
        }
        return i;
    }

    private Trip tripFromFinalEventCollection(TripEventCollection ec) {
        Trip trip = new Trip();
        trip.connectionId = ec.trip().conn().getID();

        Comparator<TripEvent> eventStopIdxComparator = Comparator.comparingInt(e -> e.trip().stopIndex());
        trip.from = tripStopFromEvent(ec.events().stream().min(eventStopIdxComparator).orElseThrow(), ArrivalOrDeparture.DEPARTURE);
        trip.to = tripStopFromEvent(ec.events().stream().max(eventStopIdxComparator).orElseThrow(), ArrivalOrDeparture.ARRIVAL);

        return trip;
    }

    private TripStop tripStopFromEvent(TripEvent event, ArrivalOrDeparture type) {
        TripStop stop = new TripStop();
        stop.routeIndex = getClosestStopIndex(event);
        ConnectionEntity conn = event.trip().trip().conn();
        ConnectionEntity.StopTimeAccessor stopTime = conn.stopTimeAt(stop.routeIndex);
        stop.stopId = stopTime.getRouteStop().getStopPost().getLongLinearID();
        stop.time = TimetableUtils.getStopTimeByDispatch(conn, event.trip().trip().dispatchDay(), stop.routeIndex, type, event.event().getEventTime().getOffset());
        return stop;
    }

    private List<CICOEvent> sortEventsTopologically(List<CICOEvent> events) {
        Set<CICOEvent> knownEvents = Set.copyOf(events);

        GraphBuilder<CICOEvent, DefaultEdge, ? extends DirectedAcyclicGraph<CICOEvent, DefaultEdge>> graphBuilder = DirectedAcyclicGraph.createBuilder(DefaultEdge.class);
        for (CICOEvent event : events) {
            graphBuilder.addVertex(event);
        }
        for (CICOEvent event : events) {
            CICOEvent parent = event.getPredecessor();
            if (parent != null && knownEvents.contains(parent)) {
                graphBuilder.addEdge(parent, event);
            }
        }

        DirectedAcyclicGraph<CICOEvent, DefaultEdge> graph = graphBuilder.build();

        List<Graph<CICOEvent, DefaultEdge>> components = splitGraphToComponents(graph);
        ObjectIntMap<CICOEvent> componentIdxs = new ObjectIntHashMap<>();
        for (int i = 0; i < components.size(); i++) {
            for (CICOEvent event : components.get(i).vertexSet()) {
                componentIdxs.put(event, i);
            }
        }

        List<List<CICOEvent>> componentTopologicalOrdering = new ArrayList<>();
        for (Graph<CICOEvent, DefaultEdge> component : components) {
            componentTopologicalOrdering.add(topologicalSort(component));
        }

        ObjectIntMap<CICOEvent> componentPositions = new ObjectIntHashMap<>();
        for (List<CICOEvent > componentEvents : componentTopologicalOrdering) {
            for (int j = 0; j < componentEvents.size(); j++) {
                componentPositions.put(componentEvents.get(j), j);
            }
        }

        return events
                .stream()
                .sorted(Comparator
                        .comparingInt(componentIdxs::get)
                        .thenComparingInt(componentPositions::get)
                )
                .toList();
    }

    private <V, E> List<Graph<V, E>> splitGraphToComponents(Graph<V, E> graph) {
        ConnectivityInspector<V, E> inspector = new ConnectivityInspector<>(graph);
        List<Set<V>> connectedSets = inspector.connectedSets();

        List<Graph<V, E>> components = new ArrayList<>();
        for (Set<V> componentVertices : connectedSets) {
            components.add(new AsSubgraph<>(graph, componentVertices));
        }

        return components;
    }

    private <V, E> List<V> topologicalSort(Graph<V, E> graph) {
        List<V> sortedVertices = new ArrayList<>();
        TopologicalOrderIterator<V, E> iterator = new TopologicalOrderIterator<>(graph);
        while (iterator.hasNext()) {
            sortedVertices.add(iterator.next());
        }
        return sortedVertices;
    }

    private List<TripEventCollection> splitEventsByTrips(List<CICOEvent> events) {
        Map<TripAtDay, List<TripEvent>> eventsByTrip = new HashMap<>();

        for (CICOEvent event : events) {
            LwtCICOTicketMetadata lwtInfo = LwtCICOTicketMetadata.parse(event.getLwtMetadata());
            LwtCICOTicketMetadata lwtInfoForTrip = lwtInfo;
            if (event.getEventType() == CICOEventType.CHECK_OUT && lwtInfo.checkOutType == LwtCICOTicketMetadata.CheckOutType.SELF) {
                lwtInfoForTrip = LwtCICOTicketMetadata.parse(Objects.requireNonNull(event.getPredecessor()).getLwtMetadata());
            }
            TripLookupService.Result trip = tripLookup.lookupByLwtData(lwtInfoForTrip, event.getEventTime());
            if (trip == null) {
                throw new IllegalArgumentException("Tariff calculation is no longer possible - trip for event " + event.getLwtMetadata() + " can not be found in the timetable!");
            }
            eventsByTrip.computeIfAbsent(trip.trip(), k -> new ArrayList<>()).add(new TripEvent(event, lwtInfo, trip));
        }

        List<TripEventCollection> baseCollections = eventsByTrip
                .entrySet()
                .stream()
                .map(e -> new TripEventCollection(e.getKey(), e.getValue()))
                .toList();

        List<TripEventCollection> contiguousCollections = new ArrayList<>();
        for (TripEventCollection ec : baseCollections) {
            contiguousCollections.addAll(splitNonContiguousTripEvents(ec));
        }

        return contiguousCollections;
    }

    private List<TripEventCollection> splitNonContiguousTripEvents(TripEventCollection events) {
        List<TripEventCollection> result = new ArrayList<>();
        List<TripEvent> currentCollection = new ArrayList<>();

        for (int i = 0; i < events.events().size(); i++) {
            if (i == 0 || shouldSplitBetweenEvents(events.events().get(i - 1), events.events().get(i))) {
                if (!currentCollection.isEmpty()) {
                    result.add(new TripEventCollection(events.trip(), currentCollection));
                    currentCollection = new ArrayList<>();
                }
            }
            currentCollection.add(events.events().get(i));
        }

        // will never be empty
        result.add(new TripEventCollection(events.trip(), currentCollection));

        return result;
    }

    private boolean shouldSplitBetweenEvents(TripEvent prev, TripEvent next) {
        if (!prev.event().getSessionId().equals(next.event().getSessionId())) {
            return false;
        }
        int prevStopIdx = getClosestStopIndex(prev);
        int nextStopIdx = getClosestStopIndex(next);
        // traversed a lot of stops without an event in-between
        return Math.abs(prevStopIdx - nextStopIdx) > 1 && Duration.between(prev.event().getEventTime(), next.event().getEventTime()).toMinutes() > 10;
    }

    private Journey debugRreconstructJourneyFromEvents() {
        // debugging
        Journey j = new Journey();
        Trip t = new Trip();
        LwtTicketMetadata lookupKey = new LwtTicketMetadata();
        lookupKey.tripKey = "100384/1059";
        lookupKey.stopCis = 28037;
        lookupKey.timetableDeparture = LocalTime.of(23, 10);
        lookupKey.locationStateCode = LwtTicketMetadata.LocationStateCode.AT_STOP;
        ConnectionEntity conn = tripLookup.lookupByLwtData(lookupKey, OffsetDateTime.of(2026, 10, 26, 23, 10, 0, 0, ZoneOffset.ofHours(2))).trip().conn();
        //ConnectionEntity conn = persistentIDs.getConnectionByPersistentID("L:CISJR:100384,CN:CISJR:1059,SD:2026-10-16,FS:crz:10034,FSI:0,FT:23:10:00,TS:crz:9998,TSI:14,TT:23:52:00");
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
        if (journey.trips.length == 0) {
            return emptyTariffResult();
        }
        return tcEngine.calculateJourneyTariff(
                journey,
                new NegotiationResolution[0],
                createPassengerInfo(),
                permittedTariffs.toArray(String[]::new)
        );
    }

    private TariffResult emptyTariffResult() {
        TariffResult tr = new TariffResult();
        tr.tickets = new JourneyTicket[0];
        return tr;
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

    private static record TripEvent(CICOEvent event, LwtTicketMetadata lwtInfo, TripLookupService.Result trip) {

    }

    private static record TripEventCollection(TripAtDay trip, List<TripEvent> events) {

        public OffsetDateTime getFirstEventTime() {
            return events().getFirst().event().getEventTime();
        }

        public boolean isFirstTimeAfter(TripEventCollection other) {
            if (events.isEmpty() || other.events().isEmpty()) {
                return false;
            }
            return getFirstEventTime().isAfter(other.getFirstEventTime());
        }
    }

    public static record Result(List<Ticket> tickets, double totalPrice) {

    }
}
