package com.verified.fix.client.publish;

import com.verified.fix.client.config.ClientProperties;
import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;
import org.fusesource.stomp.jms.StompJmsConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Publishes order updates to a STOMP broker. Only active when client.stomp.enabled=true. */
@Component
@ConditionalOnProperty(name = "client.stomp.enabled", havingValue = "true")
public class StompPublisher implements UpdatePublisher {

    private static final Logger log = LoggerFactory.getLogger(StompPublisher.class);
    private final Connection connection;

    public StompPublisher(ClientProperties props) throws Exception {
        ClientProperties.Stomp s = props.getStomp();
        StompJmsConnectionFactory factory = new StompJmsConnectionFactory();
        factory.setBrokerURI((s.isSsl() ? "ssl://" : "tcp://") + s.getHost() + ":" + s.getPort());
        factory.setUsername(s.getUsername());
        factory.setPassword(s.getPassword());
        this.connection = factory.createConnection(s.getUsername(), s.getPassword());
        this.connection.start();
        log.info("STOMP publishing is ON ({}:{})", s.getHost(), s.getPort());
    }

    @Override
    public void publish(String topicName, String json) {
        try {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic(topicName);
            MessageProducer producer = session.createProducer(topic);
            producer.send(session.createTextMessage(json));
            session.close();
            log.info("STOMP published to {}: {}", topicName, json);
        } catch (Exception e) {
            log.warn("STOMP publish to {} failed: {}", topicName, e.toString());
        }
    }
}
