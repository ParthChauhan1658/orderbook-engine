package com.example.orderbook.engine;

import com.example.orderbook.model.Order;
import com.example.orderbook.model.OrderType;
import com.example.orderbook.model.Side;
import com.example.orderbook.model.Trade;
import org.junit.jupiter.api.Test;


import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class DisruptorEngineTest {

    @Test
    void matchesOrdersInSequence() {
        List<Trade> out = new CopyOnWriteArrayList<>();

        try (DisruptorEngine engine = new DisruptorEngine(out::addAll)) {
            engine.submit(new Order(1, Side.SELL, OrderType.LIMIT, 100, 5, 1));
            engine.submit(new Order(2, Side.SELL, OrderType.LIMIT, 100, 5, 2));
            engine.submit(new Order(3, Side.BUY, OrderType.LIMIT, 100, 7, 3));
        }   // close() shutdown karta hai aur sab events process hone ka wait karta hai

        assertThat(out).hasSize(2);
        assertThat(out.get(0).makerOrderId()).isEqualTo(1);   // FIFO
        assertThat(out.get(0).quantity()).isEqualTo(5);
        assertThat(out.get(1).makerOrderId()).isEqualTo(2);
        assertThat(out.get(1).quantity()).isEqualTo(2);
    }
}