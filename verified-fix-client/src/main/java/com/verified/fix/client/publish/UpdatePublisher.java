package com.verified.fix.client.publish;

/** Something that wants to hear about order updates (for example a STOMP broker). */
public interface UpdatePublisher {
    /** @param topic the topic name, @param json the order update as JSON text */
    void publish(String topic, String json);
}
