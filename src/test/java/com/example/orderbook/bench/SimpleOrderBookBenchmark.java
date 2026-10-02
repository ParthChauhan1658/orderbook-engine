package com.example.orderbook.bench;

import com.example.orderbook.book.OrderBook;
import com.example.orderbook.book.SimpleOrderBook;
import com.example.orderbook.model.Order;
import com.example.orderbook.model.OrderType;
import com.example.orderbook.model.Side;
import com.example.orderbook.model.Trade;
import org.openjdk.jmh.annotations.*;

import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(2)
@State(Scope.Thread)
public class SimpleOrderBookBenchmark {

    @Param({"11", "1000"})
    int levels;

    private OrderBook book;
    private SplittableRandom rnd;
    private long id;

    // har iteration pe fresh book
    @Setup(Level.Iteration)
    public void setup() {
        book = new SimpleOrderBook();
        rnd = new SplittableRandom(42);
        id = 0;
    }

    @Benchmark
    public List<Trade> submit() {
        long i = ++id;
        Side side = rnd.nextBoolean() ? Side.BUY : Side.SELL;
        long price = 1 + rnd.nextInt(levels);
        long qty = 1 + rnd.nextInt(10);
        return book.submit(new Order(i, side, OrderType.LIMIT, price, qty, i));
    }
}