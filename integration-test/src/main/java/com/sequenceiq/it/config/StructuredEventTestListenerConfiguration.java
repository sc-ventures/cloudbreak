package com.sequenceiq.it.config;


import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;

import com.sequenceiq.it.EmbeddedKafkaOwn;
import com.sequenceiq.it.StructuredEventAwaiter;

@Configuration
@ComponentScan("com.sequenceiq.it")
@EnableConfigurationProperties
@EnableKafka
public class StructuredEventTestListenerConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(StructuredEventTestListenerConfiguration.class);

    @Value("${embedded.kafka.host:localhost:3333}")
    private String embeddedKafkaHost;

    @Value("${kafka.listeners:PLAINTEXT://:3333}")
    private String listeners;

    @Value("${kafka.advertised.listeners:PLAINTEXT://localhost:3333}")
    private String advertisedListener;

    @Bean
    public KafkaListenerContainerFactory<?> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setBatchListener(true);
        return factory;
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerConfigs());
    }

    @Bean
    public Map<String, Object> consumerConfigs() {
        LOGGER.info("STARTING EMBEDDED KAFKA with listeners: {} and advertised listeners: {}", listeners, advertisedListener);
        Map<String, Object> props = new HashMap<>();

        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaHost);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "1111");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        return props;
    }

    @Bean
    public EmbeddedKafkaOwn createBroker() {
        return EmbeddedKafkaOwn.createEmbeddedKafka(listeners, advertisedListener);
    }

    @Bean
    public StructuredEventAwaiter createAwaiter() {
        return new StructuredEventAwaiter();
    }
}