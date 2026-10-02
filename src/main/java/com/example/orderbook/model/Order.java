package com.example.orderbook.model;

import java.util.concurrent.atomic.AtomicLong;

public final class Order {

    private static final long CANCELLED = -1;

    private final long id;
    private final Side side;
    private final OrderType type;
    private final long price;      // ticks me (MARKET ke liye 0)
    private final long quantity;   // original quantity
    private final long sequence;   // time priority

    // >0 active, 0 filled, -1 cancelled
    private final AtomicLong remaining;

    public Order(long id, Side side, OrderType type, long price, long quantity, long sequence) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be > 0");
        if (type == OrderType.LIMIT && price <= 0) throw new IllegalArgumentException("limit price must be > 0");
        this.id = id;
        this.side = side;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
        this.sequence = sequence;
        this.remaining = new AtomicLong(quantity);
    }

    /**
     * Thread-safe fill. Actual filled qty return karta hai (0 agar order active nahi).
     * CAS fail hone par naye value ke saath retry karta hai.
     */
    public long fill(long qty) {
        while (true) {
            long current = remaining.get();
            if (current <= 0) return 0;                 // filled ya cancelled
            long filled = Math.min(qty, current);
            if (remaining.compareAndSet(current, current - filled)) {
                return filled;
            }
        }
    }

    /** true sirf tab jab is call ne order ko cancel kiya (pehle active tha). */
    public boolean cancel() {
        while (true) {
            long current = remaining.get();
            if (current <= 0) return false;            // already filled ya cancelled
            if (remaining.compareAndSet(current, CANCELLED)) {
                return true;
            }
        }
    }

    public boolean isActive() {
        return remaining.get() > 0;
    }

    public OrderState state() {
        long r = remaining.get();
        if (r == CANCELLED) return OrderState.CANCELLED;
        if (r == 0) return OrderState.FILLED;
        if (r == quantity) return OrderState.NEW;
        return OrderState.PARTIALLY_FILLED;
    }

    /** Cancelled order ke liye 0 return hota hai. */
    public long remaining() {
        return Math.max(0, remaining.get());
    }

    public long id() { return id; }
    public Side side() { return side; }
    public OrderType type() { return type; }
    public long price() { return price; }
    public long quantity() { return quantity; }
    public long sequence() { return sequence; }
}