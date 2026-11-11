package com.verified.fix.client.fix;

import com.verified.fix.client.store.OrderStore;
import org.springframework.stereotype.Component;
import quickfix.Message;
import quickfix.field.ClOrdID;
import quickfix.field.OrdType;
import quickfix.field.SecurityListRequestType;
import quickfix.field.SecurityReqID;
import quickfix.field.Side;
import quickfix.field.TransactTime;
import quickfix.fix44.MarketDataRequest;
import quickfix.fix44.NewOrderSingle;
import quickfix.fix44.OrderCancelReplaceRequest;
import quickfix.fix44.OrderCancelRequest;
import quickfix.fix44.OrderMassStatusRequest;
import quickfix.fix44.OrderStatusRequest;
import quickfix.fix44.RequestForPositions;
import quickfix.fix44.SecurityListRequest;
import quickfix.fix44.TradeCaptureReportRequest;

/**
 * One method per API. Each method builds the FIX request, sends it and returns the request id.
 * The answer arrives later as a message from the server (see {@link ReplyHandler}).
 */
@Component
public class FixApi {

    private final FixConnection connection;
    private final OrderStore orders;

    public FixApi(FixConnection connection, OrderStore orders) {
        this.connection = connection;
        this.orders = orders;
    }

    private static String newId(String prefix) {
        return prefix + System.currentTimeMillis();
    }

    private String send(Message request, String id) throws Exception {
        connection.send(request);
        return id;
    }

    // ---------- market data ----------

    /** assets: all securities. */
    public String assets() throws Exception {
        String id = newId("T-");
        return send(new SecurityListRequest(new SecurityReqID(id), new SecurityListRequestType(4)), id);
    }

    /** assetPair: the pool for one symbol/currency pair. */
    public String assetPair(String symbol, String currency) throws Exception {
        String id = newId("T-");
        SecurityListRequest r = new SecurityListRequest(new SecurityReqID(id), new SecurityListRequestType(0));
        r.setString(55, symbol);
        r.setString(15, currency);
        return send(r, id);
    }

    /** ticker: one security. */
    public String ticker(String symbol) throws Exception {
        String id = newId("T-");
        SecurityListRequest r = new SecurityListRequest(new SecurityReqID(id), new SecurityListRequestType(0));
        r.setString(55, symbol);
        return send(r, id);
    }

    /** fetchOrderBook: bids and offers of a pool. */
    public String orderBook(String symbol, String poolId) throws Exception {
        String id = newId("T-");
        MarketDataRequest r = new MarketDataRequest();
        r.setString(262, id);
        r.setChar(263, '0');   // snapshot
        r.setInt(264, 0);      // full book
        MarketDataRequest.NoMDEntryTypes bid = new MarketDataRequest.NoMDEntryTypes();
        bid.setChar(269, '0');
        r.addGroup(bid);
        MarketDataRequest.NoMDEntryTypes offer = new MarketDataRequest.NoMDEntryTypes();
        offer.setChar(269, '1');
        r.addGroup(offer);
        MarketDataRequest.NoRelatedSym sym = new MarketDataRequest.NoRelatedSym();
        sym.setString(55, symbol);
        sym.setString(FixTags.POOL_ID, poolId);
        r.addGroup(sym);
        return send(r, id);
    }

    // ---------- orders and trades (queries) ----------

    /** fetchOrder: status of one order. */
    public String order(String orderRef) throws Exception {
        String id = newId("T-");
        OrderStatusRequest r = new OrderStatusRequest();
        r.setString(11, id);
        r.setString(37, orderRef);
        r.setString(55, "NA");     // FIX requires Symbol and Side here; the server ignores them
        r.setChar(54, '1');
        return send(r, id);
    }

    /** openOrders: open orders of an account in a pool. */
    public String openOrders(String poolId, String account) throws Exception {
        String id = newId("T-");
        OrderMassStatusRequest r = new OrderMassStatusRequest();
        r.setString(584, id);
        r.setInt(585, 8);
        r.setString(FixTags.POOL_ID, poolId);
        r.setString(1, account);
        return send(r, id);
    }

    /** closedOrders: executed trades of an account in a pool. */
    public String closedOrders(String poolId, String account) throws Exception {
        return tradeReport(1, poolId, account, null);
    }

    /** trades: open orders and trades of an account in a pool. */
    public String trades(String poolId, String account) throws Exception {
        return tradeReport(0, poolId, account, null);
    }

    /** fetchTrade: one executed trade. */
    public String trade(String tradeId) throws Exception {
        return tradeReport(1, null, null, tradeId);
    }

    private String tradeReport(int subType, String poolId, String account, String tradeId) throws Exception {
        String id = newId("T-");
        TradeCaptureReportRequest r = new TradeCaptureReportRequest();
        r.setString(568, id);
        r.setInt(569, subType);
        if (poolId != null) r.setString(FixTags.POOL_ID, poolId);
        if (account != null) r.setString(1, account);
        if (tradeId != null) r.setString(37, tradeId);
        return send(r, id);
    }

    // ---------- balances ----------

    /** fetchAccountBalance: value of the holdings per currency. */
    public String accountBalance(String account) throws Exception {
        return positions(99, account);
    }

    /** fetchTradeBalance: the securities held. */
    public String tradeBalance(String account) throws Exception {
        return positions(0, account);
    }

    private String positions(int type, String account) throws Exception {
        String id = newId("T-");
        RequestForPositions r = new RequestForPositions();
        r.setString(710, id);
        r.setInt(724, type);
        r.setString(1, account);
        return send(r, id);
    }

    // ---------- order entry ----------

    /**
     * New market order. {@code account} is the broker's client (vendor) id, sent in Account (1); the server
     * needs it to place real orders. It may be null when the server runs in dry-run mode.
     */
    public String newOrder(String side, String symbol, String currency, String poolId, String quantity,
                           String account, String price) throws Exception {
        boolean buy = side.equalsIgnoreCase("Buy");
        boolean limit = price != null && !price.isBlank();
        String id = newId("CL-");
        NewOrderSingle o = new NewOrderSingle(new ClOrdID(id), new Side(buy ? Side.BUY : Side.SELL),
                new TransactTime(), new OrdType(limit ? OrdType.LIMIT : OrdType.MARKET));
        if (limit) {
            o.setString(44, price);
        }
        o.setString(55, symbol);
        o.setString(15, currency);
        o.setString(FixTags.POOL_ID, poolId);
        o.setString(38, quantity);
        if (account != null && !account.isBlank()) {
            o.setString(1, account);
        }
        orders.create(id, symbol, currency, buy ? "Buy" : "Sell", quantity);
        return send(o, id);
    }

    /** editOrder: change the price of an open order. */
    public String editOrder(String orderRef, String symbol, String currency, String side, String quantity,
                            String newPrice, String poolId, String account) throws Exception {
        String id = newId("REP-");
        OrderCancelReplaceRequest r = new OrderCancelReplaceRequest();
        fillChange(r, id, orderRef, symbol, currency, side, quantity, poolId, account);
        r.setChar(40, '1');
        r.setString(44, newPrice);
        return send(r, id);
    }

    /** cancelOrder: cancel an open order. */
    public String cancelOrder(String orderRef, String symbol, String currency, String side, String quantity,
                              String poolId, String account) throws Exception {
        String id = newId("CAN-");
        OrderCancelRequest r = new OrderCancelRequest();
        fillChange(r, id, orderRef, symbol, currency, side, quantity, poolId, account);
        return send(r, id);
    }

    private void fillChange(Message r, String id, String orderRef, String symbol, String currency, String side,
                            String quantity, String poolId, String account) {
        if (account != null && !account.isBlank()) {
            r.setString(1, account);       // Account: the vendor (client) id
        }
        if (poolId != null && !poolId.isBlank()) {
            r.setString(FixTags.POOL_ID, poolId);     // PoolID: which pool the order is in
        }
        // The original ClOrdID is whatever we know for this vendor order reference (or a placeholder).
        var known = orders.find(null, null, orderRef);
        r.setString(41, known != null ? known.clOrdId : "ORIG-" + id);
        r.setString(11, id);
        r.setString(37, orderRef);
        r.setString(55, symbol);
        r.setString(15, currency);
        r.setChar(54, side.equalsIgnoreCase("Sell") ? '2' : '1');
        r.setString(38, quantity);
        r.setField(new TransactTime());
    }
}
