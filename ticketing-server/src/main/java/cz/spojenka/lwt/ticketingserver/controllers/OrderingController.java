package cz.spojenka.lwt.ticketingserver.controllers;

import cz.spojenka.lwt.ticketing.api.OrderResponse;
import cz.spojenka.lwt.ticketingserver.model.AccountPrincipal;
import cz.spojenka.lwt.ticketingserver.model.Order;
import cz.spojenka.lwt.ticketingserver.services.OrderFacade;
import cz.spojenka.lwt.ticketingserver.services.OrderingService;
import cz.spojenka.lwt.ticketingserver.services.TicketIssuanceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@Tag(name = "Ordering", description = "Ticket ordering API")
public class OrderingController {

    private final OrderingService orderingService;
    private final TicketIssuanceService ticketIssuanceService;
    private final OrderFacade facade;

    public OrderingController(OrderingService orderingService, TicketIssuanceService ticketIssuanceService, OrderFacade facade) {
        this.orderingService = orderingService;
        this.ticketIssuanceService = ticketIssuanceService;
        this.facade = facade;
    }

    @PostMapping("/orders/new")
    public OrderResponse placeNewOrder(@RequestParam("products[]") int[] productIds, @AuthenticationPrincipal AccountPrincipal accountPrincipal) {
        Order o = orderingService.initializeOrder(accountPrincipal.requireAccount(), Arrays.stream(productIds).mapToObj(ticketIssuanceService::findProductByIdOrThrow).toList());
        return facade.exportOrder(o);
    }

    private void checkAccess(Order o, AccountPrincipal accountPrincipal) {
        if (o.getAccount().getId() != accountPrincipal.requireAccount().getId()) {
            throw new IllegalArgumentException("Order with ID " + o.getId() + " does not belong to the authenticated user");
        }
    }

    @PostMapping("/orders/{id}/test-pay")
    public OrderResponse testPayOrder(@PathVariable("id") long orderId, @AuthenticationPrincipal AccountPrincipal accountPrincipal) {
        Order o = orderingService.findOrderByIdOrThrow(orderId);
        checkAccess(o, accountPrincipal);
        orderingService.testPayOrder(o);
        return facade.exportOrder(o);
    }

    @GetMapping("/orders/{id}")
    public OrderResponse getOrderInfo(@PathVariable("id") long orderId, @AuthenticationPrincipal AccountPrincipal accountPrincipal) {
        Order o = orderingService.findOrderByIdOrThrow(orderId);
        checkAccess(o, accountPrincipal);
        return facade.exportOrder(o);
    }
}
