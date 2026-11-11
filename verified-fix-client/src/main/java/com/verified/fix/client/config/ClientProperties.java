package com.verified.fix.client.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Everything from the "client:" part of application.yml. */
@ConfigurationProperties(prefix = "client")
public class ClientProperties {

    private String vendorCode = "VERIFIED";
    private Fix fix = new Fix();
    private Stomp stomp = new Stomp();

    /** Returns the vendor code. */
    public String getVendorCode() { return vendorCode; }
    /** Sets the vendor code. */
    public void setVendorCode(String vendorCode) { this.vendorCode = vendorCode; }
    /** Returns the fix. */
    public Fix getFix() { return fix; }
    /** Sets the fix. */
    public void setFix(Fix fix) { this.fix = fix; }
    /** Returns the stomp. */
    public Stomp getStomp() { return stomp; }
    /** Sets the stomp. */
    public void setStomp(Stomp stomp) { this.stomp = stomp; }

    /** Connection settings for the FIX server. */
    public static class Fix {
        private String host = "localhost";
        private int port = 9877;
        private String senderCompId = "VERIFIEDCLIENT";
        private String targetCompId = "VERIFIEDFIX";
        private int heartbeatSeconds = 30;
        private boolean resetOnLogon = true;

        /** Returns the host. */
        public String getHost() { return host; }
        /** Sets the host. */
        public void setHost(String host) { this.host = host; }
        /** Returns the port. */
        public int getPort() { return port; }
        /** Sets the port. */
        public void setPort(int port) { this.port = port; }
        /** Returns the sender comp id. */
        public String getSenderCompId() { return senderCompId; }
        /** Sets the sender comp id. */
        public void setSenderCompId(String senderCompId) { this.senderCompId = senderCompId; }
        /** Returns the target comp id. */
        public String getTargetCompId() { return targetCompId; }
        /** Sets the target comp id. */
        public void setTargetCompId(String targetCompId) { this.targetCompId = targetCompId; }
        /** Returns the heartbeat seconds. */
        public int getHeartbeatSeconds() { return heartbeatSeconds; }
        /** Sets the heartbeat seconds. */
        public void setHeartbeatSeconds(int heartbeatSeconds) { this.heartbeatSeconds = heartbeatSeconds; }
        /** Returns the reset on logon. */
        public boolean isResetOnLogon() { return resetOnLogon; }
        /** Sets the reset on logon. */
        public void setResetOnLogon(boolean resetOnLogon) { this.resetOnLogon = resetOnLogon; }
    }

    /** Optional STOMP broker settings for publishing order updates. */
    public static class Stomp {
        private boolean enabled = false;
        private String host = "localhost";
        private int port = 61614;
        private boolean ssl = true;
        private String username = "";
        private String password = "";
        private String topicNew = "NewOrder";
        private String topicFilled = "FilledOrder";
        private String topicCancelled = "CancelledOrder";
        private String topicReplaced = "ReplacedOrder";
        private String topicRejected = "RejectedOrder";

        /** Returns the enabled. */
        public boolean isEnabled() { return enabled; }
        /** Sets the enabled. */
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        /** Returns the host. */
        public String getHost() { return host; }
        /** Sets the host. */
        public void setHost(String host) { this.host = host; }
        /** Returns the port. */
        public int getPort() { return port; }
        /** Sets the port. */
        public void setPort(int port) { this.port = port; }
        /** Returns the SSL flag. */
        public boolean isSsl() { return ssl; }
        /** Sets the SSL flag. */
        public void setSsl(boolean ssl) { this.ssl = ssl; }
        /** Returns the username. */
        public String getUsername() { return username; }
        /** Sets the username. */
        public void setUsername(String username) { this.username = username; }
        /** Returns the password. */
        public String getPassword() { return password; }
        /** Sets the password. */
        public void setPassword(String password) { this.password = password; }
        /** Returns the topic new. */
        public String getTopicNew() { return topicNew; }
        /** Sets the topic new. */
        public void setTopicNew(String v) { this.topicNew = v; }
        /** Returns the topic filled. */
        public String getTopicFilled() { return topicFilled; }
        /** Sets the topic filled. */
        public void setTopicFilled(String v) { this.topicFilled = v; }
        /** Returns the topic cancelled. */
        public String getTopicCancelled() { return topicCancelled; }
        /** Sets the topic cancelled. */
        public void setTopicCancelled(String v) { this.topicCancelled = v; }
        /** Returns the topic replaced. */
        public String getTopicReplaced() { return topicReplaced; }
        /** Sets the topic replaced. */
        public void setTopicReplaced(String v) { this.topicReplaced = v; }
        /** Returns the topic rejected. */
        public String getTopicRejected() { return topicRejected; }
        /** Sets the topic rejected. */
        public void setTopicRejected(String v) { this.topicRejected = v; }
    }
}
