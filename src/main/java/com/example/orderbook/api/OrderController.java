package com.example.orderbook.api;

import com.example.orderbook.engine.BookSnapshot;
import com.example.orderbook.engine.DisruptorEngine;
import com.example.orderbook.model.Trade;
import com.example.orderbook.persistence.OrderEntity;
import com.example.orderbook.persistence.TradeEntity;
import com.example.orderbook.persistence.TradeRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;

@RestController
public class OrderController {

    private final DisruptorEngine engine;
    private final TradeLog tradeLog;
    private final TradeRepository tradeRepository;
    private final OrderService orderService;

    public OrderController(DisruptorEngine engine, TradeLog tradeLog, TradeRepository tradeRepository, OrderService orderService) {
        this.engine = engine;
        this.tradeLog = tradeLog;
        this.tradeRepository = tradeRepository;
        this.orderService = orderService;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OrderAccepted submit(Authentication auth, @Valid @RequestBody SubmitOrderRequest req) {
        return new OrderAccepted(orderService.submit(auth.getName(), req));
    }

    @DeleteMapping("/orders/{id}")
    public ResponseEntity<Void> cancel(Authentication auth, @PathVariable long id) {
        return orderService.cancel(auth.getName(), id)
                ? ResponseEntity.accepted().build()
                : ResponseEntity.notFound().build();
    }

    @GetMapping("/orders/mine")
    public List<OrderEntity> mine(Authentication auth,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        return orderService.mine(auth.getName(), page, size).getContent();
    }

    @GetMapping("/trades")
    public List<Trade> trades(@RequestParam(defaultValue = "50") int limit) {
        return tradeLog.recent(Math.max(1, Math.min(limit, 500)));
    }

    @GetMapping("/trades/history")
    public List<TradeEntity> history(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        var pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)),
                Sort.by(Sort.Direction.DESC, "id"));
        return tradeRepository.findAll(pageable).getContent();
    }

    @GetMapping("/book")
    public BookSnapshot book() {
        return engine.snapshot();
    }
}