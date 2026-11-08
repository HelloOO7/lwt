package cz.spojenka.lwt.ticketingserver.services;

import cz.spojenka.lwt.ticketing.api.CICOEventType;
import cz.spojenka.lwt.ticketingserver.api.DataArchivedException;
import cz.spojenka.lwt.ticketingserver.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class CICOSessionService {

    private final CICORepository cicoRepository;
    private final CICOClearingRepository clearingRepository;
    private final TicketRepository ticketRepository;
    private final TariffCalculationService tariffCalculationService;

    @Value("${tickets.cico.allow-timeout-clearing-after}")
    private Duration allowTimeoutClearingAfter;
    @Value("${tickets.cico.force-session-clearing-after}")
    private Duration forceSessionClearingAfter;

    public CICOSessionService(CICORepository repository, CICOClearingRepository clearingRepository, TicketRepository ticketRepository, TariffCalculationService tariffCalculationService) {
        this.cicoRepository = repository;
        this.clearingRepository = clearingRepository;
        this.ticketRepository = ticketRepository;
        this.tariffCalculationService = tariffCalculationService;
    }

    public Page<CICOSessionSegment> getSessionsForAccount(Account account, boolean includeCleared, Pageable pageable) {
        return cicoRepository.findSessionsByAccountId(account.getId(), !includeCleared, pageable);
    }

    public List<CICOSessionSegment> getUnclearedSessionsForAccount(Account account) {
        return cicoRepository.findUnclearedSessionsByAccountId(account.getId());
    }

    public Page<CICOClearingRecord> getClearingsForAccount(Account account, boolean includeCancelled, Pageable pageable) {
        return clearingRepository.findByAccountId(account.getId(), includeCancelled, pageable);
    }

    public CICOClearingRecord findClearingByIdOrThrow(long clearingId) {
        return clearingRepository.findById(clearingId).orElseThrow(() -> new NoSuchElementException("Clearing not found: " + clearingId));
    }

    public List<UUID> findSessionIDsInClearing(CICOClearingRecord clearing) {
        return clearingRepository.findSessionIDsInClearing(clearing.getId());
    }

    @Transactional
    public ClearingStats runClearingForAccount(Account account) {
        List<CICOEvent> unclearedEvents = cicoRepository.getUnclearedEventsForAccount(account.getId());
        if (unclearedEvents.isEmpty()) {
            return ClearingStats.empty();
        }
        OffsetDateTime clearingTime = OffsetDateTime.now();
        Set<UUID> sessionsToSkip = getSessionsToSkipClearing(unclearedEvents, clearingTime);
        if (!sessionsToSkip.isEmpty()) {
            unclearedEvents = unclearedEvents.stream()
                    .filter(event -> !sessionsToSkip.contains(event.getSessionId()))
                    .toList();
        }
        var tariffResult = tariffCalculationService.calculateCICOTariff(unclearedEvents);
        CICOClearingRecord clearingRecord = new CICOClearingRecord(account, clearingTime, tariffResult.tickets(), tariffResult.totalPrice());
        clearingRepository.save(clearingRecord);
        unclearedEvents.forEach(event -> event.clear(clearingRecord));
        cicoRepository.saveAll(unclearedEvents);
        int numClearedSections = (int) unclearedEvents.stream().map(CICOEvent::getSessionId).distinct().count();
        return new ClearingStats(unclearedEvents.size(), numClearedSections, 1, tariffResult.totalPrice());
    }

    private Set<UUID> getSessionsToSkipClearing(List<CICOEvent> events, OffsetDateTime clearingTime) {
        Set<UUID> checkedOutSessions = new HashSet<>();
        Map<UUID, CICOEvent> newestEvents = new HashMap<>();
        Map<UUID, CICOEvent> oldestEvents = new HashMap<>();
        for (CICOEvent e : events) {
            if (e.getEventType() == CICOEventType.CHECK_OUT) {
                checkedOutSessions.add(e.getSessionId());
            }
            newestEvents.compute(e.getSessionId(), (sessionId, existing) -> {
                if (existing == null || e.getEventTime().isAfter(existing.getEventTime())) {
                    return e;
                }
                return existing;
            });
            oldestEvents.compute(e.getSessionId(), (sessionId, existing) -> {
                if (existing == null || e.getEventTime().isBefore(existing.getEventTime())) {
                    return e;
                }
                return existing;
            });
        }
        Set<UUID> result = new HashSet<>();
        for (var newest : newestEvents.entrySet()) {
            if (Duration.between(newest.getValue().getEventTime(), clearingTime).compareTo(allowTimeoutClearingAfter) < 0) {
                result.add(newest.getKey());
            }
        }
        for (var oldest : oldestEvents.entrySet()) {
            // even if we did not meet the condition above, after this time the whole session must be cleared
            if (Duration.between(oldest.getValue().getEventTime(), clearingTime).compareTo(forceSessionClearingAfter) > 0) {
                result.remove(oldest.getKey());
            }
        }
        // sessions with explicit checkouts should always be cleared
        result.removeAll(checkedOutSessions);
        return result;
    }

    @Transactional
    public ClearingStats runGlobalClearing() {
        ClearingStats result = ClearingStats.empty();
        try (var unclearedAccounts = cicoRepository.findAccountsWithUnclearedEvents()) {
            var it = unclearedAccounts.iterator();
            while (it.hasNext()) {
                result = result.add(runClearingForAccount(it.next()));
            }
        }
        return result;
    }

    @Transactional
    public void undoClearing(CICOClearingRecord clearing) {
        if (!clearing.canBeCancelled()) {
            throw new IllegalStateException("Clearing " + clearing.getId() + " is already cancelled.");
        }
        List<CICOEvent> clearedEvents = clearing.getClearedEvents();
        if (!tariffCalculationService.canCalculateCICOTariff(clearedEvents)) {
            throw new DataArchivedException("Attempted to undo clearing of a session whose tariff can no longer be calculated. This data has been frozen and can not be modified any more.");
        }
        clearedEvents.forEach(CICOEvent::undoClearing);
        clearing.cancel();
        clearingRepository.save(clearing);
    }
}
