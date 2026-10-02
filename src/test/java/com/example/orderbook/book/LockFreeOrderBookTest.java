package com.example.orderbook.book;

class LockFreeOrderBookTest extends OrderBookContractTest {

    @Override
    protected OrderBook createBook() {
        return new LockFreeOrderBook();
    }
}