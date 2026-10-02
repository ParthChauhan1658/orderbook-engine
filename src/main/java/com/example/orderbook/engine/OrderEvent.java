package com.example.orderbook.engine;

import com.example.orderbook.model.Order;

/** Ring buffer ka reusable slot. Naya object har baar nahi banta, sirf fields overwrite hote hain. */
public final class OrderEvent {

    public enum Type { SUBMIT, CANCEL, RESTORE }

    Type type;
    Order order;       // SUBMIT ke liye
    long cancelId;     // CANCEL ke liye

    void clear() {
        type = null;
        order = null;
        cancelId = 0;
    }
}