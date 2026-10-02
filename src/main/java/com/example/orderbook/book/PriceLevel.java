package com.example.orderbook.book;

import com.example.orderbook.model.Order;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/** Ek price ka FIFO queue. size = queue me orders ki ginti, -1 matlab level band (closed). */
final class PriceLevel {

    private final ConcurrentLinkedQueue<Order> queue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger size = new AtomicInteger();

    /** false agar level close ho chuka hai. */
    boolean tryAdd(Order order) {
        while (true) {
            int s = size.get();
            if (s < 0) return false;
            if (size.compareAndSet(s, s + 1)) {
                queue.add(order);
                return true;
            }
        }
    }

    Order peek() {
        return queue.peek();
    }

    /** Order queue se nikalta hai. size sirf tab ghatata hai jab isi call ne nikala. */
    void remove(Order order) {
        if (queue.remove(order)) {
            size.decrementAndGet();
        }
    }

    /** Sirf tab close hota hai jab level bilkul khaali ho. */
    boolean tryClose() {
        return size.compareAndSet(0, -1);
    }

    boolean isClosed() {
        return size.get() < 0;
    }
}