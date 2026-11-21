package cz.spojenka.lwt.ticketing.api;

import java.io.Serializable;

public record OrderResponse(long id, float totalPrice, String paymentURL, OrderStatus status) implements Serializable {
}
