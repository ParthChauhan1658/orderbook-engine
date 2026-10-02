package com.example.orderbook.bench;

import com.example.orderbook.engine.DisruptorEngine;
import com.example.orderbook.model.Order;
import com.example.orderbook.model.OrderType;
import com.example.orderbook.model.Side;
import org.openjdk.jmh.annotations.*;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(2)
public class DisruptorEngineBenchmark {

    private static final AtomicLong BLOCKS = new AtomicLong();   // sirf setup me, hot path me nahi

    @State(Scope.Benchmark)
    public static class Shared {
        @Param({"11", "1000"})
        int levels;

        DisruptorEngine engine;

        @Setup(Level.Iteration)
        public void setup() {
            engine = new DisruptorEngine(trades -> {});   // sink khaali
        }

        @TearDown(Level.Iteration)
        public void tearDown() {
            engine.close();   // bacha hua backlog drain hone tak wait
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
    public void submit1(Shared s, PerThread t) { run(s, t); }

    @Benchmark @Threads(4)
    public void submit4(Shared s, PerThread t) { run(s, t); }

    @Benchmark @Threads(8)
    public void submit8(Shared s, PerThread t) { run(s, t); }

    private void run(Shared s, PerThread t) {
        long id = ++t.nextId;
        Side side = t.rnd.nextBoolean() ? Side.BUY : Side.SELL;
        long price = 1 + t.rnd.nextInt(s.levels);
        long qty = 1 + t.rnd.nextInt(10);
        s.engine.submit(new Order(id, side, OrderType.LIMIT, price, qty, id));
    }
}