package com.example.orderbook.model;

public record Trade(long makerOrderId, long takerOrderId, long price, long quantity, long sequence) {}