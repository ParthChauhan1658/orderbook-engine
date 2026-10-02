package com.example.orderbook.api;

import com.example.orderbook.engine.DisruptorEngine;
import com.example.orderbook.model.Order;
import com.example.orderbook.model.OrderType;
import com.example.orderbook.persistence.OrderEntity;
import com.example.orderbook.persistence.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final OrderRepository repository;
    private final DisruptorEngine engine;

    public OrderService(OrderRepository repository, DisruptorEngine engine) {
        this.repository = repository;
        this.engine = engine;
    }

    public long submit(String username, SubmitOrderRequest req) {
        long price = req.type() == OrderType.MARKET ? 0 : req.price();

        // validation: galat order DB me na jaye (IllegalArgumentException -> 400)
        new Order(1, req.side(), req.type(), price, req.quantity(), 1);

        OrderEntity saved = repository.save(
                new OrderEntity(username, req.side(), req.type(), price, req.quantity()));
        long id = saved.getId();

        engine.submit(new Order(id, req.side(), req.type(), price, req.quantity(), id));
        return id;
    }

    /** true agar order is user ka tha. Doosre ka ya unknown ho to false (controller 404 dega). */
    @Transactional
    public boolean cancel(String username, long id) {
        var found = repository.findByIdAndUsername(id, username);
        if (found.isEmpty()) return false;
        found.get().markCancelled();
        engine.cancel(id);
        return true;
    }

    public Page<OrderEntity> mine(String username, int page, int size) {
        return repository.findByUsername(username,
                PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)),
                        Sort.by(Sort.Direction.DESC, "id")));
    }
}