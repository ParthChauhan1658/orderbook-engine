package com.example.orderbook.book;

import com.example.orderbook.model.Order;
import com.example.orderbook.model.OrderType;
import com.example.orderbook.model.Side;
import com.example.orderbook.model.Trade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

public class LockFreeOrderBook implements OrderBook {

    private final ConcurrentSkipListMap<Long, PriceLevel> bids =
            new ConcurrentSkipListMap<>(Comparator.reverseOrder());
    private final ConcurrentSkipListMap<Long, PriceLevel> asks = new ConcurrentSkipListMap<>();
    private final ConcurrentHashMap<Long, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong tradeSeq = new AtomicLong();

    @Override
    public List<Trade> submit(Order order) {
        List<Trade> trades = new ArrayList<>();
        var opposite = order.side() == Side.BUY ? asks : bids;

        match(order, opposite, trades);

        if (order.type() == OrderType.LIMIT && order.remaining() > 0) {
            rest(order);
        }
        return trades;
    }

    private void match(Order taker, ConcurrentSkipListMap<Long, PriceLevel> opposite, List<Trade> trades) {
        // entrySet iterator weakly consistent hai: concurrent changes me crash nahi karta
        for (Map.Entry<Long, PriceLevel> entry : opposite.entrySet()) {
            if (taker.remaining() == 0) break;

            long price = entry.getKey();
            if (!crosses(taker, price)) break;

            PriceLevel level = entry.getValue();
            Order maker;
            while (taker.remaining() > 0 && (maker = level.peek()) != null) {
                if (!maker.isActive()) {            // cancelled ya kisi aur ne fill kar diya
                    level.remove(maker);
                    continue;
                }
                long qty = maker.fill(taker.remaining());   // CAS loop andar hai
                if (qty == 0) {                      // race haar gaye, order ab active nahi
                    level.remove(maker);
                    continue;
                }
                taker.fill(qty);
                trades.add(new Trade(maker.id(), taker.id(), price, qty, tradeSeq.incrementAndGet()));

                if (!maker.isActive()) {             // poora fill
                    level.remove(maker);
                    orders.remove(maker.id());
                }
            }

            if (level.peek() == null) {
                if (level.isClosed() || level.tryClose()) {
                    opposite.remove(price, level);   // sirf tab hatao jab map me wahi level ho
                }
            }
        }
    }

    private void rest(Order order) {
        var own = order.side() == Side.BUY ? bids : asks;
        orders.put(order.id(), order);               // level me daalne se PEHLE, taaki cancel mil sake
        while (true) {
            PriceLevel level = own.computeIfAbsent(order.price(), p -> new PriceLevel());
            if (level.tryAdd(order)) return;
            own.remove(order.price(), level);        // closed level hatao, naya banao, retry
        }
    }

    private boolean crosses(Order taker, long levelPrice) {
        if (taker.type() == OrderType.MARKET) return true;
        return taker.side() == Side.BUY
                ? taker.price() >= levelPrice
                : taker.price() <= levelPrice;
    }

    @Override
    public boolean cancel(long orderId) {
        Order order = orders.remove(orderId);
        if (order == null) return false;
        return order.cancel();   // true sirf tab jab cancel match se pehle jeeta
    }

    @Override
    public OptionalLong bestBid() {
        return bestPrice(bids);
    }

    @Override
    public OptionalLong bestAsk() {
        return bestPrice(asks);
    }

    private OptionalLong bestPrice(ConcurrentSkipListMap<Long, PriceLevel> side) {
        for (Map.Entry<Long, PriceLevel> entry : side.entrySet()) {
            PriceLevel level = entry.getValue();
            Order head;
            while ((head = level.peek()) != null) {
                if (head.isActive()) return OptionalLong.of(entry.getKey());
                level.remove(head);                  // lazy cancelled order saaf karo
            }
            if (level.isClosed() || level.tryClose()) {
                side.remove(entry.getKey(), level);
            }
        }
        return OptionalLong.empty();
    }
}