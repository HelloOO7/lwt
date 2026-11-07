package cz.spojenka.lwt.ticketingserver.services;

import cz.spojenka.lwt.ticketingserver.model.CICOEvent;
import cz.spojenka.lwt.ticketingserver.model.CICORepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class TariffCalculationService {

    private final AccountService accountService;
    private final CICORepository cicoRepository;
    private final ObjectProvider<CICOTariffCalculation> cicoCalcProvider;

    public TariffCalculationService(AccountService accountService, CICORepository cicoRepository, ObjectProvider<CICOTariffCalculation> cicoCalcProvider) {
        this.accountService = accountService;
        this.cicoRepository = cicoRepository;
        this.cicoCalcProvider = cicoCalcProvider;
    }

    public CICOTariffCalculation.Result calculateTariffForCICOSession(UUID sessionId) {
        return calculateCICOTariff(cicoRepository.findAllInSession(sessionId));
    }

    public boolean canCalculateCICOTariff(List<CICOEvent> events) {
        if (events.isEmpty()) {
            return false;
        }
        return cicoCalcProvider.getObject(events).canCalculate();
    }

    public CICOTariffCalculation.Result calculateCICOTariff(List<CICOEvent> events) {
        return cicoCalcProvider.getObject(events).calculate();
    }
}
