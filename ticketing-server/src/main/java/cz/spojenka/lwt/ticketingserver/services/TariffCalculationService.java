package cz.spojenka.lwt.ticketingserver.services;

import cz.spojenka.lwt.ticketing.api.CICOEventType;
import cz.spojenka.lwt.ticketingserver.model.CICOEvent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class TariffCalculationService {

    private final AccountService accountService;
    private final ObjectProvider<CICOTariffCalculation> cicoCalcProvider;

    public TariffCalculationService(AccountService accountService, ObjectProvider<CICOTariffCalculation> cicoCalcProvider) {
        this.accountService = accountService;
        this.cicoCalcProvider = cicoCalcProvider;
    }

    @Transactional
    public void test() {
        System.out.println(calculateCICOTariff(List.of(
                new CICOEvent(UUID.randomUUID(), null, UUID.randomUUID(), accountService.findByIdOrThrow(1), OffsetDateTime.now(), CICOEventType.CHECK_IN, "")
        )));
    }

    public CICOTariffCalculation.Result calculateCICOTariff(List<CICOEvent> events) {
        return cicoCalcProvider.getObject(events).calculate();
    }
}
