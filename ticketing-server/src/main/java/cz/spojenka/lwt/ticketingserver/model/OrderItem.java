package cz.spojenka.lwt.ticketingserver.model;

import jakarta.persistence.*;

@Entity
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Order order;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Product product;

    @OneToOne(mappedBy = "orderedBy", optional = true)
    private Ticket ticket;

    OrderItem() {

    }

    public OrderItem(Order order, Product product) {
        this.order = order;
        this.product = product;
    }

    public Order getOrder() {
        return order;
    }

    public Product getProduct() {
        return product;
    }

    public Ticket getTicket() {
        return ticket;
    }
}
