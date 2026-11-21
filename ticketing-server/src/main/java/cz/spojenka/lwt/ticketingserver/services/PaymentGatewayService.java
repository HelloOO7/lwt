package cz.spojenka.lwt.ticketingserver.services;

import cz.spojenka.lwt.ticketingserver.model.Order;
import org.springframework.stereotype.Service;

@Service
public class PaymentGatewayService {

    public PaymentGatewayService() {

    }

    public String initPayment(Order order) {
        return "test/" + order.getId();
    }

    public boolean isTestPayment(String paymentReference) {
        return paymentReference.startsWith("test/");
    }

    public String getPaymentUrl(String paymentReference) {
        return "https://example.com/pay/" + paymentReference;
    }
}
