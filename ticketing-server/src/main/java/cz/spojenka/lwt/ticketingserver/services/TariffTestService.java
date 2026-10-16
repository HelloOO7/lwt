package cz.spojenka.lwt.ticketingserver.services;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class TariffTestService {

    private final TariffCalculationService parent;

    public TariffTestService(TariffCalculationService parent) {
        this.parent = parent;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void doTest() {
        parent.test();
    }
}
