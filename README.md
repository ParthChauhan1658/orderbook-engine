# Order Book Engine

[![CI](https://github.com/ParthChauhan1658/orderbook-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/ParthChauhan1658/orderbook-engine/actions/workflows/ci.yml)

A price-time-priority matching engine in Java 25. Orders are matched by a single thread fed from an LMAX Disruptor ring buffer, and exposed through a Spring Boot REST API with JWT auth and PostgreSQL persistence.

## Architecture

```
HTTP -> OrderController -> OrderService -> orders table (id, owner)
                                 |
                                 v
                         DisruptorEngine (ring buffer, many producers)
                                 |
                                 v
                  SimpleOrderBook (one matching thread, no locks)
                       |                      |
                       v                      v
          volatile BookSnapshot        TradeWriter queue
          (GET /book)                          |
                                               v
                              TradePersister -> trades + order status

Startup: OrderRecovery reads open orders from the DB and restores the book.
```

## Design decisions

- **Single-writer matching.** Price-time priority needs one total order of events, so one thread owns the book. Producers only publish to the ring buffer. The core packages (`model`, `book`, `engine`) have no Spring dependency.
- **Database off the hot path.** The matching thread never touches the DB. Trades go to a bounded queue and a separate writer thread persists them in batches. If the queue is full, trades are dropped and counted rather than stalling matching (see limitations).
- **Lock-free book kept as a comparison.** `LockFreeOrderBook` (ConcurrentSkipListMap, ConcurrentLinkedQueue, CAS on order state) passes the same contract tests and a concurrent stress test. A close marker on each price level prevents orders being added to a level that is being removed.
- **Reads without locks.** The matching thread publishes an immutable top-5 `BookSnapshot` through a volatile reference after each batch, so `GET /book` is safe and slightly stale by design.
- **Recovery.** On startup, open LIMIT orders are loaded from the DB (id order) and restored directly into the book without re-matching.
- **Auth.** BCrypt password hashes, stateless JWT. A user can only cancel or list their own orders; someone else's order returns 404.

## Benchmarks

JMH, Java 25, i7-14650HX (hybrid CPU), 2 forks, 5 measurement iterations. Throughput of `submit` including `Order` allocation, price range 1..levels, ops/sec.

| Design | Threads | levels=11 | levels=1000 |
|---|---|---|---|
| SimpleOrderBook (direct call) | 1 | 10.65M | 8.03M |
| LockFreeOrderBook | 1 | 6.72M | 5.43M |
| LockFreeOrderBook | 4 | 5.86M | 4.90M |
| LockFreeOrderBook | 8 | 5.13M | 4.54M |
| DisruptorEngine | 1 | 5.56M | 5.16M |
| DisruptorEngine | 4 | 5.45M | 4.59M |
| DisruptorEngine | 8 | 4.86M | 4.50M |

What the numbers show:

- No concurrent design beat the plain single-threaded book on this workload.
- Both concurrent designs get slower as producer threads increase (negative scaling).
- More price levels slows every design, including the single-threaded one, so that is a book-depth effect, not a concurrency effect.

What they do not explain:

- An earlier Disruptor run measured 9.6M ops/s with one producer, versus 5.56M in the final run. The benchmark changed between runs (per-thread id ranges, different price range), but I have not isolated the cause.
- I suspect allocation and GC pressure (each op creates an `Order` plus an `AtomicLong`) and shared counters, but I have not profiled it. These are hypotheses.
- These are throughput numbers. Tail latency (p99, p99.9) is not measured.

## Run

```
cp .env.example .env     # edit the values
docker compose up -d --build
```

API on http://localhost:8080. For local development, run only the database (`docker compose up -d db`) and start the app from your IDE.

## API

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | /auth/register, /auth/login | no | returns a JWT |
| POST | /orders | yes | submit LIMIT or MARKET order (202, async) |
| DELETE | /orders/{id} | yes | cancel own order (404 if not yours) |
| GET | /orders/mine | yes | own orders, paginated |
| GET | /book | yes | top 5 levels per side |
| GET | /trades | yes | recent trades (in memory) |
| GET | /trades/history | yes | persisted trades, paginated |

Send `Authorization: Bearer <token>`.

## Tests

```
./mvnw test
```

74 tests: shared contract tests run against both book implementations, multi-threaded stress tests (quantity conservation), MockMvc API tests, and auth and ownership tests. Tests run on H2, so no database is needed. CI runs them on every push.

Benchmarks are in `src/test/java/.../bench` and run through `BenchmarkRunner`.

## Known limitations

- Cancel is marked in the DB immediately but applied in the engine asynchronously, so an order that fills in between can show the wrong status.
- If the trade writer queue fills up, trades are dropped (counted, not persisted). A write-ahead log would fix this.
- MARKET orders keep status `NEW` in the DB; their unfilled remainder is discarded.
- A crash can lose fills that were queued but not yet written.
- The lock-free book can briefly show a crossed book under concurrent submits, and its time priority is only approximate across threads.
- Schema is created by Hibernate `ddl-auto=update`; there are no migrations.