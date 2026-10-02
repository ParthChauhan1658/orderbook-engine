package com.example.orderbook.api;

import com.example.orderbook.model.OrderType;
import com.example.orderbook.model.Side;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SubmitOrderRequest(
        @NotNull Side side,
        @NotNull OrderType type,
        long price,                    // MARKET ke liye ignore hota hai
        @Positive long quantity) {
}