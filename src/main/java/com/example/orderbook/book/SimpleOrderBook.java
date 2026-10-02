package com.example.orderbook.book;

import com.example.orderbook.model.Order;
import com.example.orderbook.model.OrderType;
import com.example.orderbook.model.Side;
import com.example.orderbook.model.Trade;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.TreeMap;

public class SimpleOrderBook implements OrderBook {

    private final TreeMap<Long, ArrayDeque<Order>> bids = new TreeMap<>(Collections.reverseOrder());
    private final TreeMap<Long, ArrayDeque<Order>> asks = new TreeMap<>();
    private final Map<Long, Order> orders = new HashMap<>();
    private long tradeSeq = 0;

    @Override
    public List<Trade> submit(Order order) {
        List<Trade> trades = new ArrayList<>();
        TreeMap<Long, ArrayDeque<Order>> opposite = order.side() == Side.BUY ? asks : bids;

        match(order, opposite, trades);

        // bacha hua LIMIT qty book me rest karega, MARKET ka bacha hua discard
        if (order.type() == OrderType.LIMIT && order.remaining() > 0) {
            TreeMap<Long, ArrayDeque<Order>> own = order.side() == Side.BUY ? bids : asks;
            own.computeIfAbsent(order.price(), p -> new ArrayDeque<>()).addLast(order);
            orders.put(order.id(), order);
        }
        return trades;
    }

    private void match(Order taker, TreeMap<Long, ArrayDeque<Order>> opposite, List<Trade> trades) {
        while (taker.remaining() > 0 && !opposite.isEmpty()) {
            var bestEntry = opposite.firstEntry();
            long levelPrice = bestEntry.getKey();

            if (!crosses(taker, levelPrice)) break;

            ArrayDeque<Order> queue = bestEntry.getValue();

            while (taker.remaining() > 0 && !queue.isEmpty()) {
                Order maker = queue.peekFirst();

                if (!maker.isActive()) {          // lazily cancelled order
                    queue.pollFirst();
                    continue;
                }

                long qty = Math.min(taker.remaining(), maker.remaining());
                maker.fill(qty);
                taker.fill(qty);
                trades.add(new Trade(maker.id(), taker.id(), levelPrice, qty, ++tradeSeq));

                if (!maker.isActive()) {          // poora fill ho gaya
                    queue.pollFirst();
                    orders.remove(maker.id());
                }
            }

            if (queue.isEmpty()) {
                opposite.remove(levelPrice);
            }
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
        if (order == null || !order.isActive()) return false;
        order.cancel();
        return true;
    }

    @Override
    public OptionalLong bestBid() {
        return bestPrice(bids);
    }

    @Override
    public OptionalLong bestAsk() {
        return bestPrice(asks);
    }

    /** Top level se cancelled orders hata ke real best price deta hai. */
    private OptionalLong bestPrice(TreeMap<Long, ArrayDeque<Order>> side) {
        while (!side.isEmpty()) {
            var entry = side.firstEntry();
            ArrayDeque<Order> queue = entry.getValue();
            while (!queue.isEmpty() && !queue.peekFirst().isActive()) {
                queue.pollFirst();
            }
            if (!queue.isEmpty()) return OptionalLong.of(entry.getKey());
            side.remove(entry.getKey());
        }
        return OptionalLong.empty();
    }

    /** Top n price levels ka (price, total remaining qty). Best price pehle. Cancelled orders skip hote hain. */
    public List<long[]> depth(boolean bidSide, int n) {
        var side = bidSide ? bids : asks;
        List<long[]> out = new ArrayList<>(n);
        for (var entry : side.entrySet()) {
            if (out.size() == n) break;
            long total = 0;
            for (Order o : entry.getValue()) {
                total += o.remaining();   // cancelled order ka remaining() 0 hai
            }
            if (total > 0) out.add(new long[]{entry.getKey(), total});
        }
        return out;
    }

    /** Recovery ke liye: order ko match kiye bina resting book me daalta hai. */
    public void restore(Order order) {
        var own = order.side() == Side.BUY ? bids : asks;
        own.computeIfAbsent(order.price(), p -> new ArrayDeque<>()).addLast(order);
        orders.put(order.id(), order);
    }
}