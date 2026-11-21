package cz.spojenka.lwt.ticketingserver.model;

import cz.spojenka.lwt.ticketing.api.OrderStatus;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders") // ORDER is a SQL keyword
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY)
    private Account account;

    private OrderStatus status;
    private String paymentReference;

    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<OrderItem> items = new ArrayList<>();

    Order() {

    }

    public Order(Account account) {
        this.account = account;
        this.status = OrderStatus.CREATED;
        this.items = new ArrayList<>();
    }

    public long getId() {
        return id;
    }

    public Account getAccount() {
        return account;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public float calculateTotalPrice() {
        return (float) items.stream().mapToDouble(item -> item.getProduct().getPrice()).sum();
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public void addItem(Product product) {
        OrderItem item = new OrderItem(this, product);
        items.add(item);
    }
}
