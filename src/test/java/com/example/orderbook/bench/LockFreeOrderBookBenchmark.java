package com.example.orderbook.bench;

import com.example.orderbook.book.LockFreeOrderBook;
import com.example.orderbook.model.Order;
import com.example.orderbook.model.OrderType;
import com.example.orderbook.model.Side;
import com.example.orderbook.model.Trade;
import org.openjdk.jmh.annotations.*;

import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(2)
public class LockFreeOrderBookBenchmark {

    private static final AtomicLong BLOCKS = new AtomicLong();   // sirf setup me use hota hai, hot path me nahi

    @State(Scope.Benchmark)
    public static class Shared {
        @Param({"11", "1000"})
        int levels;

        LockFreeOrderBook book;

        @Setup(Level.Iteration)
        public void setup() {
            book = new LockFreeOrderBook();
        }
    }

    @State(Scope.Thread)
    public static class PerThread {
        SplittableRandom rnd;
        long nextId;

        @Setup(Level.Trial)
        public void setup() {
            rnd = new SplittableRandom(System.nanoTime());
            nextId = BLOCKS.getAndIncrement() << 40;   // har thread ki apni id range
        }
    }

    @Benchmark @Threads(1)
    public List<Trade> submit1(Shared s, PerThread t) { return run(s, t); }

    @Benchmark @Threads(4)
    public List<Trade> submit4(Shared s, PerThread t) { return run(s, t); }

    @Benchmark @Threads(8)
    public List<Trade> submit8(Shared s, PerThread t) { return run(s, t); }

    private List<Trade> run(Shared s, PerThread t) {
        long id = ++t.nextId;
        Side side = t.rnd.nextBoolean() ? Side.BUY : Side.SELL;
        long price = 1 + t.rnd.nextInt(s.levels);
        long qty = 1 + t.rnd.nextInt(10);
        return s.book.submit(new Order(id, side, OrderType.LIMIT, price, qty, id));
    }
}