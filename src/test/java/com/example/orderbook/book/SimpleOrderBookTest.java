package com.example.orderbook.book;

class SimpleOrderBookTest extends OrderBookContractTest {

    @Override
    protected OrderBook createBook() {
        return new SimpleOrderBook();
    }
}