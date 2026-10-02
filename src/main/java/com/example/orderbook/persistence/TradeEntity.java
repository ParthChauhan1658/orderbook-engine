package com.example.orderbook.persistence;

import com.example.orderbook.model.Trade;
import jakarta.persistence.*;

@Entity
@Table(name = "trades")
public class TradeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private long makerOrderId;
    private long takerOrderId;
    private long price;
    private long quantity;
    private long engineSequence;   // restart pe 0 se shuru hota hai, isliye id alag hai

    protected TradeEntity() {}     // JPA ke liye

    public TradeEntity(Trade t) {
        this.makerOrderId = t.makerOrderId();
        this.takerOrderId = t.takerOrderId();
        this.price = t.price();
        this.quantity = t.quantity();
        this.engineSequence = t.sequence();
    }

    public Long getId() { return id; }
    public long getMakerOrderId() { return makerOrderId; }
    public long getTakerOrderId() { return takerOrderId; }
    public long getPrice() { return price; }
    public long getQuantity() { return quantity; }
    public long getEngineSequence() { return engineSequence; }
}