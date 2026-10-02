package com.example.orderbook.persistence;

import com.example.orderbook.model.Trade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TradePersister {

    private final TradeRepository tradeRepository;
    private final OrderRepository orderRepository;

    public TradePersister(TradeRepository tradeRepository, OrderRepository orderRepository) {
        this.tradeRepository = tradeRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public void persist(List<Trade> trades) {
        tradeRepository.saveAll(trades.stream().map(TradeEntity::new).toList());

        Map<Long, Long> fills = new HashMap<>();
        for (Trade t : trades) {
            fills.merge(t.makerOrderId(), t.quantity(), Long::sum);
            fills.merge(t.takerOrderId(), t.quantity(), Long::sum);
        }
        for (OrderEntity o : orderRepository.findAllById(fills.keySet())) {
            o.applyFill(fills.get(o.getId()));   // managed entity, commit pe khud update hoga
        }
    }
}