package com.example.orderbook.persistence;

import com.example.orderbook.model.OrderType;
import com.example.orderbook.model.Side;
import jakarta.persistence.*;

@Entity
@Table(name = "orders")   // "order" SQL ka reserved word hai
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private Side side;

    @Enumerated(EnumType.STRING)
    private OrderType type;

    private long price;
    private long quantity;
    private String status = "NEW";
    private long filledQuantity;

    protected OrderEntity() {}

    public OrderEntity(String username, Side side, OrderType type, long price, long quantity) {
        this.username = username;
        this.side = side;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public Side getSide() { return side; }
    public OrderType getType() { return type; }
    public long getPrice() { return price; }
    public long getQuantity() { return quantity; }
    public String getStatus() { return status; }
    public long getFilledQuantity() { return filledQuantity; }

    public void applyFill(long qty) {
        filledQuantity += qty;
        if (!"CANCELLED".equals(status)) {
            status = filledQuantity >= quantity ? "FILLED" : "PARTIALLY_FILLED";
        }
    }

    public void markCancelled() {
        if ("NEW".equals(status) || "PARTIALLY_FILLED".equals(status)) {
            status = "CANCELLED";
        }
    }

    @Column(nullable = false)
    private String username;

    public String getUsername() { return username; }
}