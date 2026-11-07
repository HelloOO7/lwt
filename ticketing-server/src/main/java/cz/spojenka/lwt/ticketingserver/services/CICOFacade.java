package cz.spojenka.lwt.ticketingserver.services;

import cz.spojenka.datasources.model.timetable.StopEntity;
import cz.spojenka.datasources.model.timetable.TimetableDatabase;
import cz.spojenka.lwt.ticketing.api.*;
import cz.spojenka.lwt.ticketingserver.model.*;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class CICOFacade {

    private final TimetableDatabase tt;
    private final TicketFacade ticketFacade;

    public CICOFacade(TimetableDatabase tt, TicketFacade ticketFacade) {
        this.tt = tt;
        this.ticketFacade = ticketFacade;
    }

    public String getCICOStopName(CICOEvent event) {
        if (event == null) {
            return null;
        }
        LwtCICOTicketMetadata metadata = LwtCICOTicketMetadata.parse(event.getLwtMetadata());
        if (metadata.stopCis != 0) {
            StopEntity stop = tt.getStopByCis(metadata.stopCis);
            if (stop != null) {
                return stop.getName();
            }
        }
        return null;
    }

    public CICOSessionInfo exportSessionInfo(CICOSessionSegment seg) {
        return new CICOSessionInfo(
                seg.sessionId(),
                seg.checkIn().getEventTime(),
                seg.checkOut() != null ? seg.checkOut().getEventTime() : null,
                getCICOStopName(seg.checkIn()),
                getCICOStopName(seg.checkOut()),
                seg.checkOut() != null ? exportCheckOutType(seg.checkOut()) : SessionCheckOutType.TIMED_OUT
        );
    }

    public SessionCheckOutType exportCheckOutType(CICOEvent checkOut) {
        if (checkOut.getEventType() == CICOEventType.BE_OUT) {
            return SessionCheckOutType.BE_OUT;
        } else if (checkOut.getEventType() == CICOEventType.CHECK_OUT) {
            LwtCICOTicketMetadata metadata = LwtCICOTicketMetadata.parse(checkOut.getLwtMetadata());
            if (metadata.checkOutType == LwtCICOTicketMetadata.CheckOutType.SELF) {
                return SessionCheckOutType.CHECK_OUT_SELF;
            } else {
                return SessionCheckOutType.CHECK_OUT_DEVICE;
            }
        } else {
            return SessionCheckOutType.TIMED_OUT;
        }
    }

    public CICOClearedFare exportClearedFare(CICOClearingRecord clearing, List<UUID> sessions) {
        return new CICOClearedFare(
                clearing.getTickets().stream().map(this::exportCICOTicket).toList(),
                sessions,
                clearing.getTotalCost(),
                clearing.getClearedAt()
        );
    }

    public CICOTicketResponse exportCICOTicket(Ticket ticket) {
        return new CICOTicketResponse(
                ticketFacade.exportTicket(ticket, false, false),
                exportCICOTravelRecord(ticket.getActivationRecord() != null ? ticket.getActivationRecord().getActivationSourceMetadata() : null)
        );
    }

    public FareTravelRecord exportCICOTravelRecord(String travelRecordString) {
        if (travelRecordString == null) {
            return null;
        }
        List<TravelRecordString> sections = TravelRecordString.splitMultiple(travelRecordString);
        return new FareTravelRecord(sections.stream().map(this::exportTravelRecordSection).filter(Objects::nonNull).toList());
    }

    private FareSection exportTravelRecordSection(TravelRecordString sectionString) {
        if (sectionString.backup == null) {
            return null;
        }
        TripDescBackup tripDesc = TripDescBackup.parse(sectionString.backup);
        return new FareSection(
                tripDesc.lineFriendlyName,
                tripDesc.connectionNumber,
                exportTravelRecordPoint(tripDesc.fromStopName, tripDesc.startTime, tripDesc.getParsedFromStopZones()),
                exportTravelRecordPoint(tripDesc.toStopName, tripDesc.endTime, tripDesc.getParsedToStopZones())
        );
    }

    private FareSection.Point exportTravelRecordPoint(String stopName, ZonedDateTime time, List<String> zones) {
        if (stopName == null || time == null) {
            return null;
        }
        return new FareSection.Point(stopName, time.toOffsetDateTime(), zones);
    }
}
