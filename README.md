# paper-trade — Algo Trading System

A two-service algorithmic trading system for **short-to-medium swing trades**
(intraday to ~3–5 day holds) on Interactive Brokers. It is intentionally **not**
a low-latency/HFT system — the design favours decoupling, durability, and
backtest/live parity over speed.

## Architecture

The system splits the **brains** from the **hands**: the strategy decides *what*
to trade, the execution engine decides whether that's *allowed* and turns it into
broker reality. Only the engine ever talks to the broker.

```
                  ┌───────────────────────────┐
                  │   IB Gateway / TWS         │   ← IBKR account
                  └─────────────▲─────────────┘
                                │ TWS API (one socket, one writer)
        ┌───────────────────────┴────────────────────────────┐
        │  engine/   — Java / Spring Boot   ("the hands")     │
        │  IBKR connection · market data · order lifecycle ·  │
        │  risk / pre-trade checks (gatekeeper) · portfolio · │
        │  bracket orders (entry+SL+TP) · REST query API      │
        └───▲─────────────────────────────────┬──────────────┘
   OrderIntent│                                │ Bar / Fill / OrderEvent
   CancelIntent│      message broker (planned) │
            ┌──┴──────────────────────────────▼──┐
            │  strategy/  — Python   ("the brains")  (planned)
            │  indicators / models → entry, SL, TP, size
            │  same code path for backtest and live
            └─────────────────────────────────────┘
```

See the architecture decision record in the project memory for the full rationale
(communication design, idempotency via `intentId`, bracket orders, persistence,
reconciliation, backtest/live parity, ops).

## Repository layout

```
.
├── engine/        # Java/Spring Boot execution engine (this is the only service today)
├── strategy/      # Python strategy service                      (planned)
├── contracts/     # shared OrderIntent / OrderEvent schemas       (planned)
├── deploy/        # docker-compose: engine, strategy, broker, DBs (planned)
└── README.md
```

## engine/ — execution engine

The execution engine connects to IBKR TWS/Gateway and exposes trading via both an
interactive shell and an authenticated REST API.

### Prerequisites
- **Java 25** (the Maven wrapper handles Maven itself)
- **IB Gateway or TWS** running locally with the API enabled, listening on the
  paper-trading port `7497` (use `7496` for live)

### Configuration (`engine/src/main/resources/application.properties`)
| Property | Default | Notes |
|---|---|---|
| `ibkr.host` | `127.0.0.1` | TWS/Gateway host |
| `ibkr.port` | `7497` | 7497 = paper, 7496 = live |
| `ibkr.client-id` | `1` | unique per connection |
| `trade.api.username` | `trader` | override via `TRADE_API_USERNAME` |
| `trade.api.password` | `change-me` | override via `TRADE_API_PASSWORD` — **change before exposing** |

### Build & run
```bash
cd engine
./mvnw spring-boot:run     # starts the engine + interactive shell
./mvnw test                # run unit tests
```

### Shell commands
`status` · `connect` · `disconnect` · `portfolio` · `orders` · `buy <sym> <qty> [price]`
· `sell <sym> <qty> [price]` · `cancel <orderId>` · `market <sym>` · `hist <sym>`

### REST API
All endpoints require HTTP Basic auth. State-changing actions are `POST`.

```bash
# Buy (market / limit)
curl -u trader:change-me -X POST "http://localhost:8080/trade/buy?symbol=AAPL&qty=10"
curl -u trader:change-me -X POST "http://localhost:8080/trade/buy?symbol=AAPL&qty=10&price=185.50"

# Sell
curl -u trader:change-me -X POST "http://localhost:8080/trade/sell?symbol=AAPL&qty=10"

# Cancel an order
curl -u trader:change-me -X POST "http://localhost:8080/trade/cancel?orderId=14"

# Open orders (read)
curl -u trader:change-me "http://localhost:8080/trade/orders"
```

Validation: `symbol` required, `qty >= 1`, `price > 0` if present (otherwise `400`).
Missing/invalid credentials return `401`; wrong HTTP method returns `405`.

## Roadmap

1. Persistence (Postgres journal + time-series store) and startup reconciliation
2. Risk module + global kill-switch
3. Bracket-order builder: `(entry, SL, TP, size) → IBKR bracket`
4. Message broker + versioned `OrderIntent` / `OrderEvent` contract
5. Bar aggregation + time-series persistence
6. Python `strategy/` skeleton (`on_bar`) — one strategy on paper, backtest on the same code

## Notes
- Order placement is fire-and-forget against IBKR: a "submitted" response confirms the
  order was *sent*, not filled. Real outcomes arrive asynchronously via the order-status
  and error callbacks.
- IB Gateway force-logs-out daily; for unattended operation automate login with
  [IBC](https://github.com/IbcAlpha/IBC).
