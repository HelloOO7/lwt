package cz.spojenka.lwt.ticketingserver.services;

import cz.spojenka.lwt.ticketing.api.OrderResponse;
import cz.spojenka.lwt.ticketingserver.model.Order;
import org.springframework.stereotype.Service;

@Service
public class OrderFacade {

    private final PaymentGatewayService paymentGatewayService;

    public OrderFacade(PaymentGatewayService paymentGatewayService) {
        this.paymentGatewayService = paymentGatewayService;
    }

    public OrderResponse exportOrder(Order order) {
        return new OrderResponse(
                order.getId(),
                order.calculateTotalPrice(),
                order.getPaymentReference() != null ? paymentGatewayService.getPaymentUrl(order.getPaymentReference()) : null,
                order.getStatus()
        );
    }
}
