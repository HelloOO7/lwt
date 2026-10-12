package cz.spojenka.lwt.ticketingserver.services;

import cz.spojenka.lwt.ticketingserver.model.CICOEvent;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TariffCalculationService {

    private final ObjectProvider<CICOTariffCalculation> cicoCalcProvider;

    public TariffCalculationService(ObjectProvider<CICOTariffCalculation> cicoCalcProvider) {
        this.cicoCalcProvider = cicoCalcProvider;
    }

    private CICOTariffCalculation newCicoTariffCalculation(List<CICOEvent> events) {
        return cicoCalcProvider.getObject(events);
    }
}
