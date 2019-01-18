package com.sequenceiq.it;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.test.rule.KafkaEmbedded;

public class EmbeddedKafkaOwn extends KafkaEmbedded {

    public static final String DEFAULT_HOST = "localhost";

    public static final int DEFAULT_PORT = 3333;

    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddedKafkaOwn.class);

    public EmbeddedKafkaOwn(int count) {
        super(count);
    }

    public static EmbeddedKafkaOwn createEmbeddedKafka(String listener, String advertisedListener) {
        EmbeddedKafkaOwn broker = new EmbeddedKafkaOwn(1);
        Map<String, String> brokerProperties = new HashMap<>();
        brokerProperties.put("listeners", listener);
        brokerProperties.put("advertised.listeners", advertisedListener);
        brokerProperties.put("auto.create.topics.enable", "true");

        broker.brokerProperties(brokerProperties);
        return broker;
    }

    @Override
    public void before() throws Exception {
        // lame hack
        if (!isPortInUse(DEFAULT_HOST, DEFAULT_PORT)) {
            LOGGER.info("Starting kafka on host:post {}:{}", DEFAULT_HOST, DEFAULT_PORT);
            super.before();
        }
    }

    private boolean isPortInUse(String host, int port) {
        // Assume no connection is possible.
        boolean result = false;

        try {
            (new Socket(host, port)).close();
            result = true;
        } catch (SocketException e) {
            LOGGER.trace("Kafka probably already started", e);
        } catch (UnknownHostException e) {
            LOGGER.trace("Wrong host checked:", e);
        } catch (IOException e) {
            LOGGER.trace("Can't open kafka socket:", e);
        }
        return result;
    }

}
