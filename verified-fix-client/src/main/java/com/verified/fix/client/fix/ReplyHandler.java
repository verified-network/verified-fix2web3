package com.verified.fix.client.fix;

import com.verified.fix.client.config.ClientProperties;
import com.verified.fix.client.publish.UpdatePublisher;
import com.verified.fix.client.store.OrderRecord;
import com.verified.fix.client.store.OrderStore;
import com.verified.fix.client.store.ReplyStore;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import quickfix.DataDictionary;
import quickfix.Message;

/** Receives every message from the server: remembers it, updates the order state, pushes updates. */
@Component
public class ReplyHandler {

    private static final Logger log = LoggerFactory.getLogger(ReplyHandler.class);

    private final OrderStore orders;
    private final ReplyStore replies;
    private final ClientProperties props;
    private final Optional<UpdatePublisher> publisher;
    private final DataDictionary dictionary;

    public ReplyHandler(OrderStore orders, ReplyStore replies, ClientProperties props,
                        Optional<UpdatePublisher> publisher) throws Exception {
        this.orders = orders;
        this.replies = replies;
        this.props = props;
        this.publisher = publisher;
        this.dictionary = new DataDictionary("FIX44.xml");   // only used to show field names
    }

    /** Stores a message received from the server and updates the matching order. */
    public void onMessage(Message message) {
        String raw = message.toString().replace('\u0001', '|');
        Map<String, String> fields = new LinkedHashMap<>();
        String msgType = "?";
        for (String pair : message.toString().split("\u0001")) {
            int eq = pair.indexOf('=');
            if (eq < 1) continue;
            String tag = pair.substring(0, eq);
            String value = pair.substring(eq + 1);
            if (tag.equals("35")) msgType = value;
            if (tag.equals("8") || tag.equals("9") || tag.equals("10") || tag.equals("49") || tag.equals("56")
                    || tag.equals("34") || tag.equals("52")) continue;   // header/trailer noise
            String name = nameOf(tag);
            fields.putIfAbsent(name, value);   // first value wins (repeating groups keep their first row)
        }
        replies.add(msgType, fields, raw);
        log.info("Received 35={} {}", msgType, raw);
        if ("8".equals(msgType)) {
            onExecutionReport(message);
        }
    }

    private String nameOf(String tag) {
        try {
            String n = dictionary.getFieldName(Integer.parseInt(tag));
            return n == null ? tag : n;
        } catch (Exception e) {
            return tag;
        }
    }

    /** ExecutionReport (35=8): update the order and tell the listeners. */
    private void onExecutionReport(Message m) {
        String clOrdId = get(m, 11);
        String origClOrdId = get(m, 41);
        String orderId = get(m, 37);
        String execType = get(m, 150);

        OrderRecord rec;
        synchronized (orders) {
            rec = orders.find(clOrdId, origClOrdId, orderId);
            if (rec == null) {
                // Replies such as "No open orders" carry no ClOrdID and no real order: nothing to track.
                String key = (clOrdId != null && !clOrdId.isBlank()) ? clOrdId
                        : (orderId != null && !orderId.isBlank() && !"NONE".equals(orderId)) ? orderId : null;
                if (key == null) {
                    return;
                }
                rec = orders.create(key, get(m, 55), get(m, 15), sideOf(get(m, 54)), get(m, 38));
            }
            if (orderId != null && !orderId.isEmpty() && !"NONE".equals(orderId)) rec.orderId = orderId;
            rec.execType = execType;
            rec.ordStatus = get(m, 39);
            rec.status = statusOf(execType, rec.ordStatus);
            if (get(m, 14) != null) rec.cumQty = get(m, 14);
            if (get(m, 6) != null) rec.avgPx = get(m, 6);
            if (get(m, 198) != null) rec.transactionHash = get(m, 198);
            rec.text = get(m, 58);
            rec.updatedAt = Instant.now();
        }
        String topic = topicOf(execType);
        if (topic != null && publisher.isPresent()) {
            publisher.get().publish(topic, toJson(rec));
        }
    }

    private String topicOf(String execType) {
        ClientProperties.Stomp s = props.getStomp();
        if (execType == null) return null;
        switch (execType) {
            case "A": case "0": return s.getTopicNew();
            case "F": return s.getTopicFilled();
            case "4": return s.getTopicCancelled();
            case "5": return s.getTopicReplaced();
            case "8": return s.getTopicRejected();
            default: return null;
        }
    }

    static String statusOf(String execType, String ordStatus) {
        if (execType == null) return "UNKNOWN";
        switch (execType) {
            case "A": return "PENDING_NEW";
            case "0": return "NEW";
            case "F": return "FILLED";
            case "4": return "CANCELLED";
            case "5": return "REPLACED";
            case "8": return "REJECTED";
            case "E": return "PENDING_REPLACE";
            case "6": return "PENDING_CANCEL";
            case "I": return "ordStatus=" + ordStatus;   // answer to a status request
            default: return "execType=" + execType;
        }
    }

    private static String sideOf(String side) {
        if ("1".equals(side)) return "Buy";
        if ("2".equals(side)) return "Sell";
        return side;
    }

    /** The JSON sent to STOMP. "OrderID" is there because the existing order monitor looks for it. */
    private static String toJson(OrderRecord r) {
        StringBuilder sb = new StringBuilder("{");
        add(sb, "OrderID", r.orderId == null ? "" : r.orderId);
        add(sb, "ClOrdID", r.clOrdId);
        add(sb, "Symbol", r.symbol);
        add(sb, "Side", r.side);
        add(sb, "Status", r.status);
        add(sb, "OrderQty", r.quantity);
        add(sb, "CumQty", r.cumQty);
        add(sb, "AvgPx", r.avgPx);
        add(sb, "TransactionHash", r.transactionHash);
        add(sb, "Text", r.text);
        sb.setLength(sb.length() - 1);   // remove last comma
        return sb.append('}').toString();
    }

    private static void add(StringBuilder sb, String key, String value) {
        sb.append('"').append(key).append("\":");
        if (value == null) sb.append("null");
        else sb.append('"').append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        sb.append(',');
    }

    private static String get(Message m, int tag) {
        try {
            return m.isSetField(tag) ? m.getString(tag) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
