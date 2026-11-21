package cz.spojenka.lwt.ticketingserver.services;

import cz.spojenka.lwt.ticketingserver.model.*;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class TicketIssuanceService {

    private final TicketActivationService activationService;
    private final TicketRepository repository;
    private final ProductRepository productRepository;

    public TicketIssuanceService(TicketActivationService activationService, TicketRepository repository, ProductRepository productRepository) {
        this.activationService = activationService;
        this.repository = repository;
        this.productRepository = productRepository;
    }

    public Product findProductByIdOrThrow(int productId) {
        return productRepository.findById(productId).orElseThrow(() -> new IllegalArgumentException("No such product: " + productId));
    }

    public Ticket issueTicket(Account account, Product product) {
        return issueTicket(account, product, null);
    }

    public Ticket issueTicket(Account account, Product product, @Nullable OrderItem orderItem) {
        Ticket ticket = new Ticket(product, account, orderItem);
        activationService.generateActivationToken(ticket, 0);
        return repository.save(ticket);
    }

    public Ticket findTicketByIdOrThrow(long ticketId) {
        return repository.findById(ticketId).orElseThrow(() -> new IllegalArgumentException("No such ticket: " + ticketId));
    }

    public Page<Ticket> findByHolderAccount(Account account, boolean includePastValidity, Pageable pageable) {
        return repository.findAllByHolderAccount(account, OffsetDateTime.now(), includePastValidity, pageable);
    }
}
