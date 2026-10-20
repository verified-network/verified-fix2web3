# Verified FIX Server – Client Specification

**Version:** 0.1 (draft) · **Protocol:** FIX 4.4 · **Audience:** firms connecting a FIX client to the Verified FIX server

## 1. Introduction

The Verified FIX server lets a trading firm use standard FIX 4.4 to look up securities and pools, read order books, place and manage orders, and see trades and balances. You send ordinary FIX messages. You do not need to know anything about the systems behind the server.

One server serves all vendors. A tag in each request, `VendorCode (9001)`, says which vendor the request is for. This document describes the `VERIFIED` vendor.

What this document covers:

- how to connect and log on (section 2);
- the conventions used in all messages (section 3);
- the custom tags (section 4);
- every request, with the reply you get back (section 5);
- the life of an order (section 6);
- errors and rejects (section 7);
- a worked example (section 8) and what is not supported yet (section 9).

In the examples a vertical bar `|` stands for the FIX field separator (SOH, byte 0x01). Values in angle brackets, such as `<your-wallet-address>`, are placeholders for your own values.

## 2. Connecting

| Item | Value |
|---|---|
| Protocol | FIX 4.4 over plain TCP (no TLS in the current environment) |
| Host and port | `<server-host>` and `<server-port>`, given to you by Verified |
| Your SenderCompID (49) | `<your-comp-id>`, assigned by Verified, one per client |
| Server CompID (TargetCompID, 56) | `<server-comp-id>`, given to you by Verified |
| Heartbeat interval (108) | 30 seconds |
| Session hours | Every day, 00:00:00 to 23:59:59 UTC |
| Role | You are the initiator (you connect); the server is the acceptor |

**Logon.** Send a normal Logon (`35=A`) with `98=0` and `108=30`. The server answers with its own Logon. Username and password (553, 554) are not checked yet; they may become required later.

**Sequence numbers.** In the test environment you can ask for a reset by sending `141=Y` on Logon, and the server will reset both sides to 1. If you do not reset, the usual FIX rules apply: the server resends missed messages when you send a ResendRequest.

**Data dictionary.** The server uses the FIX 4.4 dictionary plus the custom tags in section 4. If your engine validates incoming messages against the standard dictionary, either add tags 9001 to 9008 as user-defined fields, or turn off checking of user-defined fields. In QuickFIX/J the setting is `ValidateUserDefinedFields=N`.

**Logout.** Send Logout (`35=5`). Orders you have placed stay active; see section 6 about updates you miss while logged out.

## 3. Conventions

**3.1 Vendor selection – `VendorCode (9001)`.** Put `9001=VERIFIED` on every application request (all messages in section 5). A request without it is rejected, because the server needs to know which vendor you mean. See section 7 for the reject you receive.

**3.2 Request ids and replies.** Every request has its own id field (for example `320` for a security list, `584` for an order list). The server copies it back in the reply, so you can match replies to requests.

**3.3 Lists.** FIX has no list message, so the server sends lists in one of two ways:

- *One message with a repeating group*, for security lists and the order book.
- *A stream of messages*, one per item, for orders and trades. Every message in the stream carries the total count and a flag showing the last one: `911` (TotNumReports) and `912` (LastRptRequested=`Y` on the last), or `748` (TotNumTradeReports) and `912` for trades.

**3.4 Empty results.** An empty result is always reported with a message, never with silence. The message differs by request and is listed with each request below.

**3.5 Numbers and times.** Quantities and prices are decimal numbers. Times are UTC. Timestamps from the vendor are converted to FIX `UTCTimestamp`.

**3.6 Asynchronous orders.** Orders are settled on a blockchain, which takes time. A new order is acknowledged at once with *Pending New*; the final outcome arrives later as another ExecutionReport. Section 6 describes this.

**3.7 Wallet addresses.** An account is identified by a wallet address, sent in `Account (1)`. You never send private keys, mnemonics or secrets over FIX, and the server never asks for them.

## 4. Custom tags

Tags you send:

| Tag | Name | Type | Meaning |
|---|---|---|---|
| 9001 | VendorCode | String | Which vendor handles the request. Use `VERIFIED`. |
| 9002 | PoolID | String | The pool (market) the request is about, as a hex id. One symbol can have several pools, so the pool id is sent for orders, order books and pool-specific queries. |

Tags the server returns in security lists (you never send them):

| Tag | Name | Type | Meaning |
|---|---|---|---|
| 9003 | PoolType | String | `PrimaryIssue` (fixed-price subscription), `SecondaryIssue` (order-book trading) or `MarginIssue` |
| 9004 | SwapFee | Float | Fee of the pool as a fraction, for example `0.01` = 1% |
| 9005 | MinimumPrice | Price | Minimum price (primary pools) |
| 9006 | SecurityOffered | Qty | Quantity on offer (primary pools) |
| 9007 | CutoffTime | Integer | End of the subscription as Unix time in seconds (primary pools). Orders after this time fail. |
| 9008 | OfferingDocs | String | Content hash (IPFS) of the offering documents |

The minimum order size of a pool uses the standard tag `MinTradeVol (562)`.

## 5. Requests and replies

### 5.1 Overview

| API | You send | You receive |
|---|---|---|
| assets (all pools) | SecurityListRequest `x`, `559=4` | SecurityList `y` |
| assetPair (one pool) | SecurityListRequest `x`, `559=0`, Symbol + Currency | SecurityList `y` |
| ticker (one security) | SecurityListRequest `x`, `559=0`, Symbol only | SecurityList `y` |
| fetchOrderBook | MarketDataRequest `V` | MarketDataSnapshotFullRefresh `W`, or MarketDataRequestReject `Y` |
| fetchOrder | OrderStatusRequest `H` | ExecutionReport `8`, `150=I` |
| openOrders | OrderMassStatusRequest `AF` | ExecutionReport `8` stream, `150=I` |
| closedOrders | TradeCaptureReportRequest `AD`, `569=1` | TradeCaptureReport `AE` stream, or Ack `AQ` |
| trades | TradeCaptureReportRequest `AD`, `569=0` | ExecutionReports for open orders, then `AE` stream, or Ack `AQ` |
| fetchTrade | TradeCaptureReportRequest `AD` with OrderID `37` | TradeCaptureReport `AE`, or Ack `AQ` |
| fetchTradeBalance | RequestForPositions `AN`, `724=0` | PositionReport `AP` stream, or Ack `AO` |
| fetchAccountBalance | RequestForPositions `AN`, `724=99` | PositionReport `AP` stream, or Ack `AO` |
| createOrder, secondaryCreateOrder | NewOrderSingle `D` | ExecutionReport `8` (Pending New, then more) |
| editOrder | OrderCancelReplaceRequest `G` | ExecutionReport `8` (Pending Replace, then more), or OrderCancelReject `9` |
| cancelOrder | OrderCancelRequest `F` | ExecutionReport `8` (Pending Cancel, then more), or OrderCancelReject `9` |

`createOrder` and `secondaryCreateOrder` are the same message for you. The server looks at the pool type and uses the right one.

### 5.2 Securities and pools

#### assets – all pools

Request:

| Tag | Name | Value |
|---|---|---|
| 35 | MsgType | `x` |
| 320 | SecurityReqID | your id |
| 559 | SecurityListRequestType | `4` (all securities) |
| 9001 | VendorCode | `VERIFIED` |

Reply: SecurityList (`35=y`).

| Tag | Name | Meaning |
|---|---|---|
| 320 | SecurityReqID | your id |
| 322 | SecurityResponseID | server id |
| 560 | SecurityRequestResult | `0` valid, `2` nothing found |
| 393 | TotNoRelatedSym | number of pools |
| 146 | NoRelatedSym | group count; each row below |
| 55 | Symbol | security symbol |
| 15 | Currency | currency symbol |
| 562 | MinTradeVol | minimum order size (when known) |
| 9002 | PoolID | pool id |
| 9003–9008 | pool details | see section 4; 9005–9008 appear for primary pools only |

The list is limited to the first 100 pools.

```
Client: 8=FIX.4.4|35=x|49=<your-comp-id>|56=<server-comp-id>|320=SL1|559=4|9001=VERIFIED|
Server: 8=FIX.4.4|35=y|320=SL1|322=SLR-1|560=0|393=2|146=2|55=ABC|15=USDC|562=1|9002=0x…01|9003=PrimaryIssue|9004=0.01|9005=0.5|9006=1000000|9007=1790808960|9008=Qm…|55=XYZ|15=USDC|9002=0x…02|9003=SecondaryIssue|9004=0.01|
```

#### assetPair – one pool

Same as *assets*, with `559=0` and both `55` (Symbol) and `15` (Currency). The reply has one row for the pool of that pair, or `560=2` and no rows when no pool exists.

```
Client: …|35=x|320=SL2|559=0|55=ABC|15=USDC|9001=VERIFIED|
```

#### ticker – one security

Same request with `559=0` and `55` (Symbol) only. The reply has one row with `55` and `48` (SecurityID = the security's token address), or `560=2` when the symbol is unknown.

### 5.3 Order book

Request: MarketDataRequest (`35=V`).

| Tag | Name | Value |
|---|---|---|
| 262 | MDReqID | your id |
| 263 | SubscriptionRequestType | `0` (snapshot; updates are not supported) |
| 264 | MarketDepth | `0` (full book) |
| 267 | NoMDEntryTypes | `2` |
| 269 | MDEntryType | `0` (bid), then `1` (offer) |
| 146 | NoRelatedSym | `1` |
| 55 | Symbol | security symbol |
| 9002 | PoolID | pool id |
| 9001 | VendorCode | `VERIFIED` |

Reply: MarketDataSnapshotFullRefresh (`35=W`) with `262`, `55`, `268` (NoMDEntries) and one row per entry: `269` (0 bid, 1 offer), `270` (price), `271` (size), `272` and `273` (date and time).

**Empty book.** FIX cannot send a snapshot without entries, so an empty book is answered with MarketDataRequestReject (`35=Y`): `262` (your id), `281=0`, and `58=Order book is empty (no bids or offers)`. Treat this as "no bids or offers".

```
Client: …|35=V|262=MD1|263=0|264=0|267=2|269=0|269=1|146=1|55=ABC|9002=0x…01|9001=VERIFIED|
Server: …|35=Y|262=MD1|281=0|58=Order book is empty (no bids or offers)|
```

### 5.4 Orders

#### fetchOrder – one order

Request: OrderStatusRequest (`35=H`).

| Tag | Name | Value |
|---|---|---|
| 11 | ClOrdID | your id |
| 37 | OrderID | order reference (the id of the order at the vendor) |
| 55 | Symbol | required by FIX; any value, for example `NA`; not used |
| 54 | Side | required by FIX; any value, for example `1`; not used |
| 9001 | VendorCode | `VERIFIED` |

Reply: ExecutionReport (`35=8`) with `150=I` (Order Status). It carries `37`, `39` (OrdStatus), `54`, `38`, `44` (price) and `1` (account) when known. If the order is not visible yet, you get `39=A` and `58=Order not yet updated, try again later`. Ask again after a short time.

#### openOrders – open orders of an account in a pool

Request: OrderMassStatusRequest (`35=AF`).

| Tag | Name | Value |
|---|---|---|
| 584 | MassStatusReqID | your id |
| 585 | MassStatusReqType | `8` (all orders) |
| 1 | Account | `<your-wallet-address>` |
| 9002 | PoolID | pool id |
| 9001 | VendorCode | `VERIFIED` |

Reply: one ExecutionReport per open order (`150=I`, `39=0`) with `37`, `54`, `38`, `44`, `60`, `584`, `911` (total) and `912` (`Y` on the last). If there are none, one ExecutionReport arrives with `37=NONE`, `55=NA`, `911=0`, `912=Y` and `58=No open orders`.

#### closedOrders, trades, fetchTrade – executed trades

Request: TradeCaptureReportRequest (`35=AD`).

| Tag | Name | Value |
|---|---|---|
| 568 | TradeRequestID | your id |
| 569 | TradeRequestType | `1` matched trades (closedOrders), `0` all (trades) |
| 1 | Account | `<your-wallet-address>` |
| 9002 | PoolID | pool id |
| 37 | OrderID | *fetchTrade only:* the trade reference; then `1` and `9002` are not needed |
| 9001 | VendorCode | `VERIFIED` |

Reply: one TradeCaptureReport (`35=AE`) per trade.

| Tag | Name | Meaning |
|---|---|---|
| 568 | TradeRequestID | your id |
| 748 / 912 | TotNumTradeReports / LastRptRequested | count; `Y` on the last |
| 571 | TradeReportID | trade reference |
| 570 | PreviouslyReported | `N` |
| 55, 32, 31 | Symbol, LastQty, LastPx | symbol, quantity, price |
| 75, 60 | TradeDate, TransactTime | date and time of the trade |
| 9002 | PoolID | pool id |
| 552 | NoSides | one side: `54` (Buy/Sell), `37` (trade reference), `1` (account), and a counterparty group `453` with `448` (counterparty wallet address), `447=D`, `452=17` |

For `trades` (`569=0`) the server first sends an ExecutionReport (`150=I`, `39=0`) for each open order, then the trade reports.

When there is nothing to report, or the trade does not exist, you get TradeCaptureReportRequestAck (`35=AQ`):

| Situation | Reply |
|---|---|
| No trades | `748=0`, `749=0`, `750=1`, `58=No trades` |
| Trade not found (fetchTrade) | `748=0`, `749=99`, `750=2`, `58=Trade not found` |

### 5.5 Balances

Request: RequestForPositions (`35=AN`).

| Tag | Name | Value |
|---|---|---|
| 710 | PosReqID | your id |
| 724 | PosReqType | `0` = securities held (fetchTradeBalance); `99` = value of holdings per currency (fetchAccountBalance) |
| 1 | Account | `<your-wallet-address>` |
| 9001 | VendorCode | `VERIFIED` |

`724=99` is a Verified-specific value, because FIX has no standard code for "value per currency".

Reply: one PositionReport (`35=AP`) per row.

| Tag | Name | Meaning |
|---|---|---|
| 721, 710 | PosMaintRptID, PosReqID | server id, your id |
| 727 | TotalNumPosReports | number of rows |
| 728 | PosReqResult | `0` |
| 1, 581 | Account, AccountType | your account, `1` |
| 55, 48, 15 | Symbol, SecurityID, Currency | what is held (`48` = token address) |
| 715 | ClearingBusinessDate | date of the last update, when known |
| 702 | NoPositions | for securities: `703=TQ`, `704` = quantity |
| 753 | NoPosAmt | `707=FMTM`, `708` = value |

When there is nothing: RequestForPositionsAck (`35=AO`) with `727=0`, `728=2` (no positions matched), `58=No positions`. An unknown `724` is answered with `728=4`.

### 5.6 Order entry

All orders are **market orders** for now. Sending another `OrdType` is rejected.

#### New order

Request: NewOrderSingle (`35=D`).

| Tag | Name | Value |
|---|---|---|
| 11 | ClOrdID | your unique id |
| 1 | Account | `<your-wallet-address>` (optional today) |
| 55 | Symbol | security symbol |
| 15 | Currency | currency symbol |
| 54 | Side | `1` Buy, `2` Sell |
| 38 | OrderQty | quantity |
| 40 | OrdType | `1` (market) |
| 60 | TransactTime | now |
| 9002 | PoolID | pool id (checked against Symbol and Currency) |
| 9001 | VendorCode | `VERIFIED` |

Reply, step 1 (arrives when the order has been submitted, which can take 20 to 30 seconds): ExecutionReport with `150=A`, `39=A` (Pending New), your `11`, `55`, `15`, `38`, and `198` (SecondaryOrderID) = the transaction hash. `58` says `Submitted to VERIFIED`. This is not a confirmation that the order succeeded. Further reports follow; see section 6.

```
Client: …|35=D|11=ORD1|55=ABC|15=USDC|54=1|38=0.01|40=1|60=20261020-07:00:00|9002=0x…01|9001=VERIFIED|
Server: …|35=8|11=ORD1|55=ABC|15=USDC|38=0.01|150=A|39=A|198=0x…txhash|58=Submitted to VERIFIED|
```

Requests on one session are handled one at a time. While the server submits an order (20 to 30 seconds), later requests on the same session wait for their turn.

#### Change an order (price)

Request: OrderCancelReplaceRequest (`35=G`).

| Tag | Name | Value |
|---|---|---|
| 11 | ClOrdID | new unique id |
| 41 | OrigClOrdID | ClOrdID of the order you want to change |
| 37 | OrderID | order reference of the order |
| 55, 15, 54, 38 | Symbol, Currency, Side, OrderQty | as in the original order |
| 40 | OrdType | `2` |
| 44 | Price | new price |
| 60 | TransactTime | now |
| 9002 | PoolID | pool id |
| 9001 | VendorCode | `VERIFIED` |

Reply: ExecutionReport `150=E`, `39=E` (Pending Replace) with `198`, then `150=5` (Replaced) when the new price shows in the open orders. If the change cannot be confirmed in time you get OrderCancelReject (`35=9`) with `434=2`.

#### Cancel an order

Request: OrderCancelRequest (`35=F`).

| Tag | Name | Value |
|---|---|---|
| 11 | ClOrdID | new unique id |
| 41 | OrigClOrdID | ClOrdID of the order |
| 37 | OrderID | order reference |
| 55, 15, 54, 38 | Symbol, Currency, Side, OrderQty | as in the order |
| 60 | TransactTime | now |
| 9002 | PoolID | pool id |
| 9001 | VendorCode | `VERIFIED` |

Reply: ExecutionReport `150=6`, `39=6` (Pending Cancel) with `198`, then `150=4` (Canceled) when the order is no longer open. If the order was executed before the cancel took effect, you get a Trade report (`150=F`) instead. If it cannot be confirmed in time: OrderCancelReject with `434=1`.

## 6. The life of an order

```
NewOrderSingle ──► Pending New (150=A) ──► New (150=0) ──► Trade (150=F, 39=2)
                         │                      │
                         │                      ├──► Pending Cancel (150=6) ──► Canceled (150=4)
                         │                      └──► Pending Replace (150=E) ──► Replaced (150=5)
                         └──► Rejected (150=8)
```

| ExecType (150) | Meaning |
|---|---|
| `A` | Pending New. The order was submitted; the transaction hash is in `198`. |
| `0` | New. The order is open (visible in the pool). `37` is the order reference; use it to change or cancel. |
| `F` | Trade. The order was executed: `32` last quantity, `31` last price, `14` cumulative quantity, `39=2` (Filled). |
| `6` / `4` | Pending Cancel / Canceled |
| `E` / `5` | Pending Replace / Replaced (new price in `44`) |
| `8` | Rejected. `103` and `58` give the reason. A market order that the vendor never fills or opens within the time limit is also reported as Rejected, with a text such as "No fill or open order seen within N s; the transaction probably failed (hash)". |

The server follows each order after the first reply and sends these reports on its own. You do not have to ask. Two things to keep in mind:

- A market order may be filled directly. Then you may get `Trade` without a `New` in between.
- If you are logged out when a report is due, the report is not delivered later when sequence numbers are reset at logon. Use `fetchOrder`, `openOrders` or `trades` to find the current state after you reconnect.

## 7. Errors and rejects

| Situation | Message | Details |
|---|---|---|
| Missing or unknown `9001` | BusinessMessageReject `35=j` | `372` = type of your message, `380=0`, `58` = "No vendor given. Send VendorCode (9001), e.g. 9001=VERIFIED" or "Unknown vendor '…'. Supported: …" |
| Missing required field in a query | BusinessMessageReject `35=j` | `58` names the field, for example "Account (1) and PoolID (9002) are required" |
| Unknown pool in `openOrders` | BusinessMessageReject `35=j` | `58=Unknown pool` |
| Vendor temporarily unavailable | BusinessMessageReject `35=j` | `58=Vendor unavailable, try again later`. Try again. |
| Order refused | ExecutionReport `150=8`, `39=8` | `103` reason code, `58` text, `37=NONE`, your `11` |
| Change or cancel not possible | OrderCancelReject `35=9` | `434` (1 cancel, 2 change), `102=99`, `58` text, your `11` and `41` |
| Order book request refused | MarketDataRequestReject `35=Y` | `281` reason, `58` text |
| Message does not follow FIX 4.4 | Reject `35=3` | Standard session-level reject |

Order reject reasons (`103`):

| Code | Meaning |
|---|---|
| 1 | Unknown symbol or currency pair |
| 11 | Order type not supported (only market orders) |
| 99 | Other; read `58` (for example a pool that does not belong to the pair, or a failure at the vendor) |

Texts in `58` are short and meant to be shown to a user. They never contain internal technical details.

## 8. Worked example

```
Client → Server   8=FIX.4.4|35=A|49=<your-comp-id>|56=<server-comp-id>|98=0|108=30|141=Y|
Server → Client   8=FIX.4.4|35=A|98=0|108=30|141=Y|

Client → Server   35=x|320=SL1|559=0|55=ABC|15=USDC|9001=VERIFIED|        (find the pool of ABC/USDC)
Server → Client   35=y|320=SL1|560=0|393=1|146=1|55=ABC|15=USDC|562=1|9002=0x…01|9003=PrimaryIssue|…|

Client → Server   35=D|11=ORD1|55=ABC|15=USDC|54=1|38=0.01|40=1|60=…|9002=0x…01|9001=VERIFIED|
Server → Client   35=8|11=ORD1|150=A|39=A|198=0x…txhash|58=Submitted to VERIFIED|      (about 20 s later)
Server → Client   35=8|11=ORD1|37=0x…ref|150=F|39=2|32=0.01|31=0.5|14=0.01|6=0.5|…       (after the vendor fills it)

Client → Server   35=AN|710=P1|724=0|1=<your-wallet-address>|9001=VERIFIED|    (what do I hold now?)
Server → Client   35=AP|710=P1|727=1|728=0|55=ABC|702=…|

Client → Server   35=5|                                                          (logout)
```

## 9. Not supported yet

- Limit orders. Only market orders can be placed. Changing the price of an open order is supported.
- Streaming market data (`263=1`) and market data updates.
- Listing more than the first 100 pools in `assets`.
- Username and password checking at logon.
- Delivery of reports that were due while you were logged out.

Questions and bug reports: please contact Verified through the repository's issue tracker or your Verified contact.
