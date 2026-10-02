# Order Book Engine

[![CI](https://github.com/ParthChauhan1658/orderbook-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/ParthChauhan1658/orderbook-engine/actions/workflows/ci.yml)

A price-time-priority matching engine in Java 25. Orders are matched by a single thread fed from an LMAX Disruptor ring buffer, and exposed through a Spring Boot REST API with JWT auth and PostgreSQL persistence. A React web UI sits on top of that API.

## Architecture

```
Browser (React + Vite)
   |  Bearer JWT, polls /book and /trades every second
   v
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

## Frontend

`frontend/` is a React 19 + Vite app in plain JavaScript. No TypeScript, no UI library, no state manager — the CSS is hand-written in `index.css` / `App.css`.

| File | Role |
|---|---|
| `api.js` | single `api()` wrapper, token in `localStorage`, 401 clears the session |
| `usePolling.js` | polls a function every `ms`; a changed dep triggers an immediate refetch |
| `Login.jsx` | register and sign in on one form |
| `Dashboard.jsx` | shell: stats row, live/offline pill, composes the cards below |
| `OrderForm.jsx` | buy/sell, limit/market, estimated total |
| `Book.jsx` | top 5 levels per side, depth bars, click a price to prefill the form |
| `Trades.jsx` | recent trades, row tinted against the previous trade |
| `MyOrders.jsx` | own orders, open/all filter, fill progress bar, cancel |

Behaviour worth knowing:

- **Polling, not WebSocket.** `/book` and `/trades?limit=20` refresh every 1000 ms, `/orders/mine` every 2000 ms. That matches the engine design: the snapshot is deliberately slightly stale, so polling costs nothing in correctness. Submitting or cancelling bumps a `refreshKey` so every panel refetches immediately instead of waiting for the next tick.
- **Price click forces LIMIT.** Picking a price from the book fills the form and switches the type to LIMIT, because a price has no meaning for a market order.
- **Stats are derived client-side.** Last price is tinted by comparing the two most recent trades; the spread is `bestAsk - bestBid` from the snapshot.
- **Auth is display-only on the client.** The JWT payload is decoded to show the username in the header; every permission decision is made by the API. A 401 from any call clears the token and returns the user to the login screen.

The dev server proxies `/api` to `http://localhost:8080` and strips the prefix, so the app always calls same-origin paths and no CORS config is needed.

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

Backend:

```
cp .env.example .env     # edit the values
docker compose up -d --build
```

API on http://localhost:8080. For local development, run only the database (`docker compose up -d db`) and start the app from your IDE.

Frontend (needs the API already running on 8080):

```
cd frontend
npm install
npm run dev             # http://localhost:5173
```

`npm run build` emits `dist/`, and `npm run lint` runs ESLint.

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
./mvnw test              # 74 backend tests on H2, no database needed
cd frontend && npm run lint
```

Backend: shared contract tests run against both book implementations, multi-threaded stress tests (quantity conservation), MockMvc API tests, and auth and ownership tests. CI runs them on every push.

Benchmarks are in `src/test/java/.../bench` and run through `BenchmarkRunner`. The frontend has no test suite; CI does not lint or build it either.

## Known limitations

- Cancel is marked in the DB immediately but applied in the engine asynchronously, so an order that fills in between can show the wrong status.
- If the trade writer queue fills up, trades are dropped (counted, not persisted). A write-ahead log would fix this.
- MARKET orders keep status `NEW` in the DB; their unfilled remainder is discarded.
- A crash can lose fills that were queued but not yet written.
- The lock-free book can briefly show a crossed book under concurrent submits, and its time priority is only approximate across threads.
- Schema is created by Hibernate `ddl-auto=update`; there are no migrations.
- The UI polls instead of subscribing, so what you see can be up to a second behind, and every open browser tab hits `/book` and `/trades` on its own interval.
- The Docker image builds the backend only. `npm run build` output is not served by Spring and nothing in the compose file ships it, so today the UI runs from the Vite dev server.
- `/trades` is a shared tape: every authenticated user sees the whole market, not just their fills. There is no per-user filtering on it.