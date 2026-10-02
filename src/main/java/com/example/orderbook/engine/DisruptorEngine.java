package com.example.orderbook.engine;

import com.example.orderbook.book.SimpleOrderBook;
import com.example.orderbook.model.Order;
import com.example.orderbook.model.Trade;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import java.util.List;
import java.util.function.Consumer;

public class DisruptorEngine implements AutoCloseable {

    private final SimpleOrderBook book = new SimpleOrderBook();
    private volatile BookSnapshot snapshot = BookSnapshot.EMPTY;

    private static final int RING_SIZE = 1 << 16;   // power of 2 hona zaroori hai

    private final Disruptor<OrderEvent> disruptor;
    private final RingBuffer<OrderEvent> ringBuffer;
    // fields me add karo
    private final AtomicLong published = new AtomicLong();
    private final AtomicLong processed = new AtomicLong();

    public DisruptorEngine(Consumer<List<Trade>> tradeSink) {
        this.disruptor = new Disruptor<>(
                OrderEvent::new,
                RING_SIZE,
                DaemonThreadFactory.INSTANCE,
                ProducerType.MULTI,                 // multiple producer threads
                new BlockingWaitStrategy());        // dev ke liye; busy-spin baad me benchmark me try karenge

        disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
            switch (event.type) {
                case SUBMIT -> {
                    List<Trade> trades = book.submit(event.order);
                    if (!trades.isEmpty()) tradeSink.accept(trades);
                }
                case CANCEL -> book.cancel(event.cancelId);
                case RESTORE -> book.restore(event.order);
            }
            if (endOfBatch) {
                snapshot = buildSnapshot();
            }
            event.clear();   // purani reference chhodo taaki GC ko rukawat na ho
            processed.incrementAndGet();
        });

        this.ringBuffer = disruptor.start();
    }

    public void submit(Order order) {
        published.incrementAndGet();
        ringBuffer.publishEvent((event, seq, o) -> {
            event.type = OrderEvent.Type.SUBMIT;
            event.order = o;
        }, order);
    }

    public void cancel(long orderId) {
        published.incrementAndGet();
        ringBuffer.publishEvent((event, seq, id) -> {
            event.type = OrderEvent.Type.CANCEL;
            event.cancelId = id;
        }, orderId);
    }

    public void restore(Order order) {
        published.incrementAndGet();
        ringBuffer.publishEvent((event, seq, o) -> {
            event.type = OrderEvent.Type.RESTORE;
            event.order = o;
        }, order);
    }

    /** Pehle bacha hua sab process hota hai, phir thread band. */
    @Override
    public void close() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (processed.get() < published.get()) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("engine drain timeout: published="
                        + published.get() + " processed=" + processed.get());
            }
            LockSupport.parkNanos(100_000);
        }
        disruptor.shutdown();
    }

    private BookSnapshot buildSnapshot() {
        return new BookSnapshot(toLevels(book.depth(true, 5)), toLevels(book.depth(false, 5)));
    }

    private static List<BookSnapshot.Level> toLevels(List<long[]> raw) {
        return raw.stream().map(a -> new BookSnapshot.Level(a[0], a[1])).toList();
    }

    public BookSnapshot snapshot() {
        return snapshot;
    }
}