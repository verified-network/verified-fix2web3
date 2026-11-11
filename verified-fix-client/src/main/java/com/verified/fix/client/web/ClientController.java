package com.verified.fix.client.web;

import com.verified.fix.client.fix.FixApi;
import com.verified.fix.client.fix.FixConnection;
import com.verified.fix.client.store.OrderRecord;
import com.verified.fix.client.store.OrderStore;
import com.verified.fix.client.store.ReplyStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Small local REST API. Every call sends a FIX request and answers {"requestId":..., "status":"SENT"}.
 * The answer from the server is read with GET /api/replies, and order states with GET /api/orders.
 * No login yet: it listens on localhost only.
 */
@RestController
@RequestMapping("/api")
public class ClientController {

    private final FixApi api;
    private final FixConnection connection;
    private final OrderStore orders;
    private final ReplyStore replies;

    public ClientController(FixApi api, FixConnection connection, OrderStore orders, ReplyStore replies) {
        this.api = api;
        this.connection = connection;
        this.orders = orders;
        this.replies = replies;
    }

    private static Map<String, String> sent(String requestId) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("requestId", requestId);
        m.put("status", "SENT");
        return m;
    }

    /** Returns whether the client is logged on to the FIX server. */
    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("loggedOn", connection.isLoggedOn());
        return m;
    }

    // ----- market data -----
    /** Asks the server for the list of assets. */
    @PostMapping("/assets")
    public Map<String, String> assets() throws Exception { return sent(api.assets()); }

    /** Asks the server for the details of one symbol and currency pair. */
    @PostMapping("/assetPair")
    public Map<String, String> assetPair(@RequestParam String symbol, @RequestParam String currency)
            throws Exception { return sent(api.assetPair(symbol, currency)); }

    /** Asks the server for the latest price of a symbol. */
    @PostMapping("/ticker")
    public Map<String, String> ticker(@RequestParam String symbol) throws Exception {
        return sent(api.ticker(symbol));
    }

    /** Asks the server for the order book of a pool. */
    @PostMapping("/orderBook")
    public Map<String, String> orderBook(@RequestParam String symbol, @RequestParam String poolId)
            throws Exception { return sent(api.orderBook(symbol, poolId)); }

    // ----- queries -----
    /** Asks the server for the status of one order. */
    @PostMapping("/order")
    public Map<String, String> order(@RequestParam String orderRef) throws Exception {
        return sent(api.order(orderRef));
    }

    /** Asks the server for the open orders of an account in a pool. */
    @PostMapping("/openOrders")
    public Map<String, String> openOrders(@RequestParam String poolId, @RequestParam String account)
            throws Exception { return sent(api.openOrders(poolId, account)); }

    /** Asks the server for the closed orders of an account in a pool. */
    @PostMapping("/closedOrders")
    public Map<String, String> closedOrders(@RequestParam String poolId, @RequestParam String account)
            throws Exception { return sent(api.closedOrders(poolId, account)); }

    /** Asks the server for the executed trades of an account in a pool. */
    @PostMapping("/trades")
    public Map<String, String> trades(@RequestParam String poolId, @RequestParam String account)
            throws Exception { return sent(api.trades(poolId, account)); }

    /** Asks the server for one executed trade. */
    @PostMapping("/trade")
    public Map<String, String> trade(@RequestParam String tradeId) throws Exception {
        return sent(api.trade(tradeId));
    }

    /** Asks the server for the balance of an account. */
    @PostMapping("/accountBalance")
    public Map<String, String> accountBalance(@RequestParam String account) throws Exception {
        return sent(api.accountBalance(account));
    }

    /** Asks the server for the trade balance of an account. */
    @PostMapping("/tradeBalance")
    public Map<String, String> tradeBalance(@RequestParam String account) throws Exception {
        return sent(api.tradeBalance(account));
    }

    // ----- order entry -----
    /** Sends a new order (Buy or Sell) to the server. */
    @PostMapping("/orders/new")
    public Map<String, String> newOrder(@RequestParam String side, @RequestParam String symbol,
                                        @RequestParam String currency, @RequestParam String poolId,
                                        @RequestParam String qty,
                                        @RequestParam(required = false) String account,
                                        @RequestParam(required = false) String price) throws Exception {
        Map<String, String> m = sent(api.newOrder(side, symbol, currency, poolId, qty, account, price));
        m.put("clOrdID", m.get("requestId"));
        return m;
    }

    /** Sends a request to change the price of an open order. */
    @PostMapping("/orders/edit")
    public Map<String, String> edit(@RequestParam String orderRef, @RequestParam String symbol,
                                    @RequestParam String currency, @RequestParam String side,
                                    @RequestParam String qty, @RequestParam String price,
                                    @RequestParam(required = false) String poolId,
                                    @RequestParam(required = false) String account) throws Exception {
        return sent(api.editOrder(orderRef, symbol, currency, side, qty, price, poolId, account));
    }

    /** Sends a request to cancel an open order. */
    @PostMapping("/orders/cancel")
    public Map<String, String> cancel(@RequestParam String orderRef, @RequestParam String symbol,
                                      @RequestParam String currency, @RequestParam String side,
                                      @RequestParam String qty,
                                      @RequestParam(required = false) String poolId,
                                      @RequestParam(required = false) String account) throws Exception {
        return sent(api.cancelOrder(orderRef, symbol, currency, side, qty, poolId, account));
    }

    // ----- results -----
    /** Returns all orders known to the client, newest first. */
    @GetMapping("/orders")
    public List<OrderRecord> orders() { return orders.all(); }

    /** Returns one order by its client order id, or 404 if unknown. */
    @GetMapping("/orders/{clOrdId}")
    public ResponseEntity<OrderRecord> order1(@PathVariable String clOrdId) {
        OrderRecord r = orders.get(clOrdId);
        return r == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(r);
    }

    /** Returns the messages received from the server after the given number. */
    @GetMapping("/replies")
    public List<ReplyStore.Reply> replies(@RequestParam(defaultValue = "0") long after) {
        return replies.since(after);
    }

    /** Turns any exception into a JSON error response. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> error(Exception e) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("status", "ERROR");
        m.put("message", String.valueOf(e.getMessage()));
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(m);
    }
}
