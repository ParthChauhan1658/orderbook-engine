package com.example.orderbook.book;

import com.example.orderbook.model.Order;
import com.example.orderbook.model.OrderType;
import com.example.orderbook.model.Side;
import com.example.orderbook.model.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

abstract class OrderBookContractTest {

    protected OrderBook book;
    private final AtomicLong seq = new AtomicLong();

    protected abstract OrderBook createBook();

    @BeforeEach
    void setUp() {
        book = createBook();
        seq.set(0);
    }

    private Order limit(long id, Side side, long price, long qty) {
        return new Order(id, side, OrderType.LIMIT, price, qty, seq.incrementAndGet());
    }

    private Order market(long id, Side side, long qty) {
        return new Order(id, side, OrderType.MARKET, 0, qty, seq.incrementAndGet());
    }

    @Test
    void restingOrdersSetBestBidAndAsk() {
        book.submit(limit(1, Side.BUY, 100, 10));
        book.submit(limit(2, Side.SELL, 105, 10));

        assertThat(book.bestBid()).hasValue(100);
        assertThat(book.bestAsk()).hasValue(105);
    }

    @Test
    void fullMatchAtMakerPrice() {
        book.submit(limit(1, Side.SELL, 100, 10));

        List<Trade> trades = book.submit(limit(2, Side.BUY, 105, 10));

        assertThat(trades).hasSize(1);
        Trade t = trades.get(0);
        assertThat(t.makerOrderId()).isEqualTo(1);
        assertThat(t.takerOrderId()).isEqualTo(2);
        assertThat(t.price()).isEqualTo(100);   // maker ka price, taker ka 105 nahi
        assertThat(t.quantity()).isEqualTo(10);
        assertThat(book.bestAsk()).isEmpty();
        assertThat(book.bestBid()).isEmpty();
    }

    @Test
    void partialFillLeavesRemainderOnBook() {
        book.submit(limit(1, Side.SELL, 100, 10));

        List<Trade> trades = book.submit(limit(2, Side.BUY, 100, 4));

        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).quantity()).isEqualTo(4);
        assertThat(book.bestAsk()).hasValue(100);   // maker ka 6 abhi baki hai
    }

    @Test
    void takerRemainderRestsOnBook() {
        book.submit(limit(1, Side.SELL, 100, 5));

        List<Trade> trades = book.submit(limit(2, Side.BUY, 100, 8));

        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).quantity()).isEqualTo(5);
        assertThat(book.bestBid()).hasValue(100);   // taker ka bacha 3 rest kar gaya
        assertThat(book.bestAsk()).isEmpty();
    }

    @Test
    void samePriceFollowsFifo() {
        book.submit(limit(1, Side.SELL, 100, 5));
        book.submit(limit(2, Side.SELL, 100, 5));

        List<Trade> trades = book.submit(limit(3, Side.BUY, 100, 7));

        assertThat(trades).hasSize(2);
        assertThat(trades.get(0).makerOrderId()).isEqualTo(1);   // pehle aaya, pehle fill
        assertThat(trades.get(0).quantity()).isEqualTo(5);
        assertThat(trades.get(1).makerOrderId()).isEqualTo(2);
        assertThat(trades.get(1).quantity()).isEqualTo(2);
    }

    @Test
    void betterPriceMatchesFirst() {
        book.submit(limit(1, Side.SELL, 102, 5));
        book.submit(limit(2, Side.SELL, 100, 5));
        book.submit(limit(3, Side.SELL, 101, 5));

        List<Trade> trades = book.submit(limit(4, Side.BUY, 102, 15));

        assertThat(trades).extracting(Trade::price).containsExactly(100L, 101L, 102L);
    }

    @Test
    void limitDoesNotCrossWhenPriceTooLow() {
        book.submit(limit(1, Side.SELL, 100, 5));

        List<Trade> trades = book.submit(limit(2, Side.BUY, 99, 5));

        assertThat(trades).isEmpty();
        assertThat(book.bestBid()).hasValue(99);
        assertThat(book.bestAsk()).hasValue(100);
    }

    @Test
    void cancelledOrderIsNotMatched() {
        book.submit(limit(1, Side.SELL, 100, 5));
        book.submit(limit(2, Side.SELL, 100, 5));

        assertThat(book.cancel(1)).isTrue();

        List<Trade> trades = book.submit(limit(3, Side.BUY, 100, 5));

        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).makerOrderId()).isEqualTo(2);
    }

    @Test
    void cancelUnknownOrderReturnsFalse() {
        assertThat(book.cancel(999)).isFalse();
    }

    @Test
    void cancelTwiceReturnsFalseSecondTime() {
        book.submit(limit(1, Side.BUY, 100, 5));

        assertThat(book.cancel(1)).isTrue();
        assertThat(book.cancel(1)).isFalse();
    }

    @Test
    void cancellingOnlyOrderClearsBestPrice() {
        book.submit(limit(1, Side.BUY, 100, 5));
        book.cancel(1);

        assertThat(book.bestBid()).isEmpty();
    }

    @Test
    void marketOrderRemainderIsDiscarded() {
        book.submit(limit(1, Side.SELL, 100, 5));

        List<Trade> trades = book.submit(market(2, Side.BUY, 8));

        assertThat(trades).hasSize(1);
        assertThat(trades.get(0).quantity()).isEqualTo(5);
        assertThat(book.bestBid()).isEmpty();   // market ka bacha 3 book me nahi rukta
        assertThat(book.bestAsk()).isEmpty();
    }

    @Test
    void marketOrderOnEmptyBookDoesNothing() {
        List<Trade> trades = book.submit(market(1, Side.SELL, 10));

        assertThat(trades).isEmpty();
        assertThat(book.bestBid()).isEmpty();
    }
}