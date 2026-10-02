package com.example.orderbook.engine;

import java.util.List;

/** Immutable snapshot. Matching thread banata hai, baaki threads bina lock ke padhte hain. */
public record BookSnapshot(List<Level> bids, List<Level> asks) {

    public record Level(long price, long quantity) {}

    public static final BookSnapshot EMPTY = new BookSnapshot(List.of(), List.of());
}