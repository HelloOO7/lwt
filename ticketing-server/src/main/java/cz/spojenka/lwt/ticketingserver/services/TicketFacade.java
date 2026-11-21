package cz.spojenka.lwt.ticketingserver.services;

import cz.spojenka.lwt.ticketing.api.ProductResponse;
import cz.spojenka.lwt.ticketing.api.TicketPayloadResponse;
import cz.spojenka.lwt.ticketing.api.TicketResponse;
import cz.spojenka.lwt.ticketingserver.model.Product;
import cz.spojenka.lwt.ticketingserver.model.Ticket;
import cz.spojenka.lwt.ticketingserver.model.TicketPayload;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class TicketFacade {

    public TicketResponse exportTicket(Ticket ticket, boolean privileged, boolean canShowPayloads) {
        return new TicketResponse(
                ticket.getId(),
                exportProduct(ticket.getProduct()),
                ticket.getHolderAccount() != null ? ticket.getHolderAccount().getId() : 0,
                ticket.getOrderedBy() != null ? ticket.getOrderedBy().getOrder().getId() : null,
                ticket.getValidSince(),
                ticket.getValidUntil(),
                ticket.getValidZones(),
                ticket.getActivationToken() != null && (privileged || ticket.getPayload() == null) ? ticket.getActivationToken().getActivationToken() : null,
                ticket.getActivationRecord() != null ? ticket.getActivationRecord().getActivationTime() : null,
                ticket.getActivationRecord() != null && privileged ? ticket.getActivationRecord().getAppId() : null,
                canShowPayloads ? createTicketPayloadResponse(ticket.getPayload()) : null
        );
    }

    private TicketPayloadResponse createTicketPayloadResponse(TicketPayload payload) {
        if (payload == null) {
            return null;
        }
        return new TicketPayloadResponse(
                payload.getEtd(),
                payload.getDerivedTotpSeed()
        );
    }

    public ProductResponse exportProduct(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getCategory(),
                product.getTitle(),
                product.getPrice(),
                product.getVatRate(),
                product.getValidFrom(),
                product.getValidTo(),
                Duration.ofMinutes(product.getValidityMinutes()),
                product.getValidityType(),
                product.getZoneCount(),
                product.getEligibleZonesString(),
                product.getExcludedZonesString(),
                product.isExcludesTrains(),
                product.getNotice()
        );
    }
}
