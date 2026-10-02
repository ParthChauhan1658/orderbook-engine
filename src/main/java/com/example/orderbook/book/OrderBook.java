package com.example.orderbook.book;

import com.example.orderbook.model.Order;
import com.example.orderbook.model.Trade;

import java.util.List;
import java.util.OptionalLong;

public interface OrderBook {

    /** Order submit karo. Match hone par trades return hote hain, bacha hua LIMIT qty book me rest karta hai. */
    List<Trade> submit(Order order);

    /** Resting order cancel karo. true agar cancel hua. */
    boolean cancel(long orderId);

    OptionalLong bestBid();

    OptionalLong bestAsk();
}