package com.example.orderbook.persistence;

import com.example.orderbook.model.Trade;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Matching thread se trades queue me aate hain, alag thread batch me DB me likhta hai. */
@Component
public class TradeWriter {

    private static final Logger log = LoggerFactory.getLogger(TradeWriter.class);
    private static final int BATCH = 500;

    private final TradePersister persister;
    private final BlockingQueue<Trade> queue = new ArrayBlockingQueue<>(100_000);
    private final AtomicLong dropped = new AtomicLong();
    private volatile boolean running = true;
    private Thread thread;

    public TradeWriter(TradePersister persister) {
        this.persister = persister;
    }

    /** Matching thread se call hota hai. Kabhi block nahi karta. */
    public void enqueue(List<Trade> trades) {
        for (Trade t : trades) {
            if (!queue.offer(t)) {
                dropped.incrementAndGet();   // queue full: matching ko rokne se behtar hai drop karke ginna
            }
        }
    }

    public long dropped() {
        return dropped.get();
    }

    @PostConstruct
    void start() {
        thread = new Thread(this::loop, "trade-writer");
        thread.start();
    }

    private void loop() {
        List<Trade> batch = new ArrayList<>(BATCH);
        while (running || !queue.isEmpty()) {
            try {
                Trade first = queue.poll(200, TimeUnit.MILLISECONDS);
                if (first == null) continue;
                batch.add(first);
                queue.drainTo(batch, BATCH - 1);
                persister.persist(batch);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.error("trade batch write failed, {} trades lost", batch.size(), e);
            } finally {
                batch.clear();
            }
        }
    }

    @PreDestroy
    void stop() throws InterruptedException {
        running = false;
        thread.join(10_000);   // bacha hua queue drain hone tak wait
    }
}