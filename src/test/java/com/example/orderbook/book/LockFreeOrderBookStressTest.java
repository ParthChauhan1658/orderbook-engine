package com.example.orderbook.book;

import com.example.orderbook.model.Order;
import com.example.orderbook.model.OrderType;
import com.example.orderbook.model.Side;
import com.example.orderbook.model.Trade;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;


import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.RepeatedTest;
class LockFreeOrderBookStressTest {

    private static final int THREADS = 8;
    private static final int ORDERS_PER_THREAD = 20_000;

    @RepeatedTest(25)
    void noOverfillAndQuantityIsConserved() throws Exception {
        LockFreeOrderBook book = new LockFreeOrderBook();
        AtomicLong ids = new AtomicLong();
        ConcurrentLinkedQueue<Order> allOrders = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Trade> allTrades = new ConcurrentLinkedQueue<>();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);

        for (int t = 0; t < THREADS; t++) {
            pool.submit(() -> {
                var rnd = ThreadLocalRandom.current();
                start.await();
                for (int i = 0; i < ORDERS_PER_THREAD; i++) {
                    long id = ids.incrementAndGet();
                    Side side = rnd.nextBoolean() ? Side.BUY : Side.SELL;
                    long price = 95 + rnd.nextInt(11);          // 95..105, overlap rakha hai
                    long qty = 1 + rnd.nextInt(10);
                    Order o = new Order(id, side, OrderType.LIMIT, price, qty, id);
                    allOrders.add(o);
                    allTrades.addAll(book.submit(o));

                    if (rnd.nextInt(10) == 0) {
                        book.cancel(1 + rnd.nextLong(id));       // random cancel
                    }
                }
                return null;
            });
        }

        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        // har order ke liye trades ka sum
        Map<Long, Long> filledByOrder = new HashMap<>();
        for (Trade tr : allTrades) {
            filledByOrder.merge(tr.makerOrderId(), tr.quantity(), Long::sum);
            filledByOrder.merge(tr.takerOrderId(), tr.quantity(), Long::sum);
        }

        List<String> violations = new ArrayList<>();
        for (Order o : allOrders) {
            long traded = filledByOrder.getOrDefault(o.id(), 0L);
            if (traded > o.quantity()) {
                violations.add("overfill id=" + o.id() + " traded=" + traded + " qty=" + o.quantity());
            }
            // cancelled order ka remaining() 0 aata hai, uski accounting alag hai, isliye skip
            if (o.state() != com.example.orderbook.model.OrderState.CANCELLED
                    && o.quantity() - o.remaining() != traded) {
                violations.add("mismatch id=" + o.id() + " traded=" + traded
                        + " filled=" + (o.quantity() - o.remaining()));
            }
        }
        assertThat(violations).isEmpty();
    }
}