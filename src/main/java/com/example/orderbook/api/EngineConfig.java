package com.example.orderbook.api;

import com.example.orderbook.engine.DisruptorEngine;
import com.example.orderbook.persistence.TradeWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EngineConfig {

    @Bean
    public TradeLog tradeLog() {
        return new TradeLog();
    }

    // app band hone par Spring close() call karta hai, jo backlog drain karke engine rokta hai
    @Bean(destroyMethod = "close")
    public DisruptorEngine disruptorEngine(TradeLog tradeLog, TradeWriter writer) {
        return new DisruptorEngine(trades -> {
            tradeLog.addAll(trades);
            writer.enqueue(trades);
        });
    }
}