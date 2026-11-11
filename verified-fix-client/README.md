# verified-fix-client

A small FIX 4.4 client for the Verified FIX server. It connects to the server, sends the 15 API
requests as FIX messages, keeps the latest state of every order, and (optionally) publishes order
updates to a STOMP broker.

```
 you (curl / browser)  --REST-->  verified-fix-client  --FIX 4.4-->  FIX server  -->  vendor
                                        |
                                        +--(optional) STOMP topics: NewOrder, FilledOrder, ...
```

## Configure

Everything that differs between clients is in `src/main/resources/application.yml`. Replace the placeholder `<your-comp-id>` with the CompID Verified gave you, and set the server host and port:

| Setting | Meaning |
|---|---|
| `client.vendor-code` | Sent in tag 9001 on every request (which vendor the server should use) |
| `client.fix.host` / `port` | Address of the FIX server |
| `client.fix.sender-comp-id` / `target-comp-id` | Who we are / who the server is |
| `client.stomp.enabled` | `true` to publish order updates to a STOMP broker |

Broker host, user and password are read from the environment variables `STOMP_HOST`, `STOMP_PORT`,
`STOMP_USER`, `STOMP_PASSWORD`. Never write them into the file.

To make another client (for example `verified-fix-pc-client`) copy this project and change only
these settings.

## Run

1. Start the FIX server.
2. Run `VerifiedFixClientApplication` (in STS: Run As > Spring Boot App), or `mvn spring-boot:run`.
3. Check `GET http://localhost:8081/api/status` shows `"loggedOn": true`.

The REST side listens on localhost only and has no login. Do not expose it.

## REST calls

Every `POST` sends one FIX request and answers `{"requestId": "...", "status": "SENT"}`.
Read the server's answer with `GET /api/replies` (add `?after=N` to see only newer ones).

| FIX API | Call |
|---|---|
| assets | `POST /api/assets` |
| assetPair | `POST /api/assetPair?symbol=XXX&currency=USDC` |
| ticker | `POST /api/ticker?symbol=XXX` |
| fetchOrderBook | `POST /api/orderBook?symbol=XXX&poolId=0x...` |
| fetchOrder | `POST /api/order?orderRef=0x...` |
| openOrders | `POST /api/openOrders?poolId=0x...&account=0x...` |
| closedOrders | `POST /api/closedOrders?poolId=0x...&account=0x...` |
| trades | `POST /api/trades?poolId=0x...&account=0x...` |
| fetchTrade | `POST /api/trade?tradeId=0x...` |
| fetchAccountBalance | `POST /api/accountBalance?account=0x...` |
| fetchTradeBalance | `POST /api/tradeBalance?account=0x...` |
| createOrder / secondaryCreateOrder | `POST /api/orders/new?side=Buy&symbol=XXX&currency=USDC&poolId=0x...&qty=0.01` |
| editOrder | `POST /api/orders/edit?orderRef=0x...&symbol=XXX&currency=USDC&side=Buy&qty=1&poolId=0x...&price=0.75` |
| cancelOrder | `POST /api/orders/cancel?orderRef=0x...&symbol=XXX&currency=USDC&side=Buy&qty=1&poolId=0x...` |

Order state: `GET /api/orders` and `GET /api/orders/{clOrdID}`.
An order moves through `SENT`, `PENDING_NEW`, `NEW`, then `FILLED`, `CANCELLED`, `REPLACED` or `REJECTED`
as the server pushes ExecutionReports.

Example (Windows):

```
curl -X POST "http://localhost:8081/api/orders/new?side=Buy&symbol=XXX&currency=USDC&poolId=0xPOOL&qty=0.01"
curl "http://localhost:8081/api/orders"
curl "http://localhost:8081/api/replies"
```

## STOMP topics (when enabled)

`NewOrder`, `FilledOrder`, `CancelledOrder`, `ReplacedOrder`, `RejectedOrder`. The JSON contains
`OrderID`, `ClOrdID`, `Symbol`, `Side`, `Status`, `OrderQty`, `CumQty`, `AvgPx`, `TransactionHash`, `Text`,
so the existing order monitor works unchanged.

## Notes

- Orders are kept in memory and are lost when the client restarts.
- `reset-on-logon: true` is for development. Use `false` in production.
- The FIX data dictionary `FIX44.xml` in `src/main/resources` includes the custom tags 9001-9008.
