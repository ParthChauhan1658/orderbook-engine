# Order Book Engine

A matching engine in Java with a single-writer (LMAX Disruptor) design, exposed through a Spring Boot REST API with JWT auth and PostgreSQL persistence.

## Architecture

```
HTTP -> OrderController -> OrderService -> orders table
                                  |
                                  v
                          DisruptorEngine -> SimpleOrderBook (single matching thread)
                                  |
                                  v
                  TradeWriter -> TradePersister -> trades + order status
Startup: OrderRecovery rebuilds the book from open orders.
```

## Run

```
cp .env.example .env     # then edit the values
docker compose up -d --build
```

API on http://localhost:8080. Register at `POST /auth/register`, then send `Authorization: Bearer <token>`.

## Endpoints

| Method | Path | Description |
|---|---|---|
| POST | /auth/register, /auth/login | get JWT |
| POST | /orders | submit LIMIT/MARKET order |
| DELETE | /orders/{id} | cancel own order |
| GET | /orders/mine | own orders |
| GET | /book | top 5 levels snapshot |
| GET | /trades, /trades/history | recent / persisted trades |

## Tests

```
./mvnw test
```