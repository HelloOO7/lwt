package cz.spojenka.lwt.ticketingserver.services;

import cz.spojenka.lwt.ticketing.api.OrderStatus;
import cz.spojenka.lwt.ticketingserver.api.AccessDeniedException;
import cz.spojenka.lwt.ticketingserver.api.NotFoundException;
import cz.spojenka.lwt.ticketingserver.model.*;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderingService {

    private final OrderRepository repository;
    private final PaymentGatewayService paymentGatewayService;
    private final TicketIssuanceService ticketIssuanceService;

    public OrderingService(OrderRepository repository, PaymentGatewayService paymentGatewayService, TicketIssuanceService ticketIssuanceService) {
        this.repository = repository;
        this.paymentGatewayService = paymentGatewayService;
        this.ticketIssuanceService = ticketIssuanceService;
    }

    public Order findOrderByIdOrThrow(long orderId) {
        return repository.findById(orderId).orElseThrow(() -> new NotFoundException("Order with ID " + orderId + " not found"));
    }

    @Transactional
    public Order initializeOrder(Account account, List<Product> products) {
        Order o = new Order(account);
        products.forEach(o::addItem);
        repository.save(o);
        String paymentRef = paymentGatewayService.initPayment(o);
        o.setPaymentReference(paymentRef);
        return repository.save(o);
    }

    @Transactional
    public void onOrderPaid(Order order) {
        if (order.getStatus() != OrderStatus.CREATED) {
            throw new IllegalStateException("Order is already paid or cancelled");
        }
        order.setStatus(OrderStatus.PAID);
        for (OrderItem item : order.getItems()) {
            if (item.getTicket() == null) {
                ticketIssuanceService.issueTicket(order.getAccount(), item.getProduct(), item);
            }
        }
    }

    @Transactional
    public void testPayOrder(Order order) {
        if (paymentGatewayService.isTestPayment(order.getPaymentReference())) {
            onOrderPaid(order);
        } else {
            throw new AccessDeniedException("Order with ID " + order.getId() + " is not a test order and cannot be paid in test mode");
        }
    }
}
