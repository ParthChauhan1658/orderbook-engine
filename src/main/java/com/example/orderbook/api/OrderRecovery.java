package com.example.orderbook.api;

import com.example.orderbook.engine.DisruptorEngine;
import com.example.orderbook.model.Order;
import com.example.orderbook.model.OrderType;
import com.example.orderbook.persistence.OrderEntity;
import com.example.orderbook.persistence.OrderRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/** Startup pe open LIMIT orders DB se padhke engine ki book rebuild karta hai. */
@Component
public class OrderRecovery {

    private static final Logger log = LoggerFactory.getLogger(OrderRecovery.class);

    private final OrderRepository repository;
    private final DisruptorEngine engine;

    public OrderRecovery(OrderRepository repository, DisruptorEngine engine) {
        this.repository = repository;
        this.engine = engine;
    }

    // Tomcat requests lena shuru kare, usse pehle chalta hai
    @PostConstruct
    void recover() {
        int count = 0;
        for (OrderEntity e : repository.findByStatusInOrderByIdAsc(List.of("NEW", "PARTIALLY_FILLED"))) {
            if (e.getType() != OrderType.LIMIT) continue;   // MARKET order book me rest nahi karta
            long remaining = e.getQuantity() - e.getFilledQuantity();
            if (remaining <= 0) continue;
            long id = e.getId();
            engine.restore(new Order(id, e.getSide(), e.getType(), e.getPrice(), remaining, id));
            count++;
        }
        log.info("Recovered {} resting orders into the book", count);
    }
}