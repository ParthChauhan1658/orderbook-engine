package com.example.orderbook.engine;

import com.example.orderbook.model.Order;
import com.example.orderbook.model.OrderType;
import com.example.orderbook.model.Side;
import com.example.orderbook.model.Trade;
import org.junit.jupiter.api.RepeatedTest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class DisruptorEngineStressTest {

    private static final int PRODUCERS = 4;
    private static final int ORDERS_PER_PRODUCER = 25_000;

    @RepeatedTest(10)
    void multiProducerConservesQuantity() throws Exception {
        // sink sirf matching thread se call hota hai, to plain list chalegi
        List<Trade> trades = new ArrayList<>();
        Map<Long, Order> orders = new ConcurrentHashMap<>();
        AtomicLong ids = new AtomicLong();

        ExecutorService pool = Executors.newFixedThreadPool(PRODUCERS);
        CountDownLatch start = new CountDownLatch(1);

        try (DisruptorEngine engine = new DisruptorEngine(trades::addAll)) {
            for (int p = 0; p < PRODUCERS; p++) {
                pool.submit(() -> {
                    var rnd = ThreadLocalRandom.current();
                    start.await();
                    for (int i = 0; i < ORDERS_PER_PRODUCER; i++) {
                        long id = ids.incrementAndGet();
                        Side side = rnd.nextBoolean() ? Side.BUY : Side.SELL;
                        long price = 95 + rnd.nextInt(11);
                        long qty = 1 + rnd.nextInt(10);
                        Order o = new Order(id, side, OrderType.LIMIT, price, qty, id);
                        orders.put(id, o);
                        engine.submit(o);
                    }
                    return null;
                });
            }
            start.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        }   // close() ke baad sab events process ho chuke hote hain

        assertThat(orders).hasSize(PRODUCERS * ORDERS_PER_PRODUCER);
        assertThat(trades).isNotEmpty();

        Map<Long, Long> tradedByOrder = new HashMap<>();
        for (Trade t : trades) {
            tradedByOrder.merge(t.makerOrderId(), t.quantity(), Long::sum);
            tradedByOrder.merge(t.takerOrderId(), t.quantity(), Long::sum);
        }

        List<String> violations = new ArrayList<>();
        for (Order o : orders.values()) {
            long traded = tradedByOrder.getOrDefault(o.id(), 0L);
            if (o.quantity() - o.remaining() != traded) {
                violations.add("mismatch id=" + o.id() + " traded=" + traded
                        + " filled=" + (o.quantity() - o.remaining()));
            }
        }
        assertThat(violations).isEmpty();
    }
}