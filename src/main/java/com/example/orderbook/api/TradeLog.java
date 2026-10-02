package com.example.orderbook.api;

import com.example.orderbook.model.Trade;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Recent trades ka bounded in-memory log. */
public class TradeLog {

    private static final int CAPACITY = 1000;
    private final ArrayDeque<Trade> trades = new ArrayDeque<>();

    public synchronized void addAll(List<Trade> batch) {
        for (Trade t : batch) {
            if (trades.size() == CAPACITY) trades.pollFirst();
            trades.addLast(t);
        }
    }

    /** Sabse naye pehle, max n. */
    public synchronized List<Trade> recent(int n) {
        List<Trade> out = new ArrayList<>(Math.min(n, trades.size()));
        var it = trades.descendingIterator();
        while (it.hasNext() && out.size() < n) out.add(it.next());
        return out;
    }
}