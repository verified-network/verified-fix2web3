package com.verified.fix.client.fix;

import com.verified.fix.client.config.ClientProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import quickfix.Application;
import quickfix.DefaultMessageFactory;
import quickfix.FileStoreFactory;
import quickfix.Message;
import quickfix.ScreenLogFactory;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.SessionNotFound;
import quickfix.SessionSettings;
import quickfix.SocketInitiator;

/** Owns the FIX session: connects at startup, logs out at shutdown, sends and receives messages. */
@Component
public class FixConnection implements Application, SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(FixConnection.class);

    private final ClientProperties props;
    private final ReplyHandler handler;
    private SocketInitiator initiator;
    private volatile SessionID sessionId;
    private volatile boolean running;

    public FixConnection(ClientProperties props, ReplyHandler handler) {
        this.props = props;
        this.handler = handler;
    }

    // ----- lifecycle: the config (host, port, CompIDs) comes from application.yml -----

    @Override
    public void start() {
        try {
            ClientProperties.Fix fix = props.getFix();
            SessionID id = new SessionID("FIX.4.4", fix.getSenderCompId(), fix.getTargetCompId());
            SessionSettings settings = new SessionSettings();
            settings.setString("ConnectionType", "initiator");
            settings.setString("StartTime", "00:00:00");
            settings.setString("EndTime", "23:59:59");
            settings.setString("HeartBtInt", String.valueOf(fix.getHeartbeatSeconds()));
            settings.setString("ReconnectInterval", "5");
            settings.setString("FileStorePath", "store");
            settings.setString("UseDataDictionary", "Y");
            settings.setString("DataDictionary", "FIX44.xml");
            settings.setString("ValidateUserDefinedFields", "N");
            settings.setString("ValidateFieldsOutOfOrder", "N");
            settings.setString("ResetOnLogon", fix.isResetOnLogon() ? "Y" : "N");
            settings.setString(id, "SocketConnectHost", fix.getHost());
            settings.setString(id, "SocketConnectPort", String.valueOf(fix.getPort()));
            initiator = new SocketInitiator(this, new FileStoreFactory(settings), settings,
                    new ScreenLogFactory(true, true, true), new DefaultMessageFactory());
            initiator.start();
            running = true;
            log.info("FIX client started: {} -> {} ({}:{})", fix.getSenderCompId(), fix.getTargetCompId(),
                    fix.getHost(), fix.getPort());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot start the FIX client: " + e.getMessage(), e);
        }
    }

    @Override
    public void stop() {
        if (initiator != null) initiator.stop();
        running = false;
    }

    @Override
    public boolean isRunning() { return running; }

    /** Returns true when the FIX session to the server is logged on. */
    public boolean isLoggedOn() { return sessionId != null; }

    /** Sends a request to the server. Tag 9001 (vendor code) is added to every request. */
    public void send(Message request) throws SessionNotFound {
        if (sessionId == null) {
            throw new IllegalStateException("Not connected to the FIX server yet");
        }
        request.setString(FixTags.VENDOR_CODE, props.getVendorCode());
        Session.sendToTarget(request, sessionId);
    }

    // ----- QuickFIX/J callbacks -----

    @Override public void onCreate(SessionID id) { }
    @Override public void onLogon(SessionID id) { sessionId = id; log.info("LOGON OK {}", id); }
    @Override public void onLogout(SessionID id) { sessionId = null; log.info("LOGOUT {}", id); }
    @Override public void toAdmin(Message message, SessionID id) { }
    /** Session-level messages. A Reject (3) or Logout (5) is recorded so it shows in /api/replies. */
    @Override
    public void fromAdmin(Message message, SessionID id) {
        try {
            String type = message.getHeader().getString(35);
            if ("3".equals(type) || "5".equals(type)) {
                handler.onMessage(message);
            }
        } catch (Exception e) {
            log.warn("Could not read admin message: {}", e.toString());
        }
    }
    @Override public void toApp(Message message, SessionID id) { }

    @Override
    public void fromApp(Message message, SessionID id) {
        try {
            handler.onMessage(message);   // every business message from the server
        } catch (Exception e) {
            log.error("Failed to handle message from the server: {}", message, e);
        }
    }
}
