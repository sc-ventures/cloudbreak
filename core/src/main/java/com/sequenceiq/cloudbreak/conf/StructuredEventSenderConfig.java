package com.sequenceiq.cloudbreak.conf;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
public class StructuredEventSenderConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(StructuredEventSenderConfig.class);

    @Value("${cb.kafka.bootstrap.servers:}")
    private String bootstrapServers;

    @Value("${cb.audit.filepath:}")
    private String auditFilePath;

    @Value("${cb.kafka.structured.events.topic:StructuredEvents}")
    private String structuredEventsTopic;

    @Value("#{'${cb.enabledplatforms:}'.split(',')}")
    private Set<String> enabledPlatforms;

    @Value("${embedded.kafka.host:localhost:3333}")
    private String embeddedKafkaHost;

    public boolean isKafkaConfigured() {
        return StringUtils.isNotEmpty(bootstrapServers) || isMockEnabled();
    }

    public boolean isFilePathConfigured() {
        return StringUtils.isNotEmpty(auditFilePath);
    }

    public String getAuditFilePath() {
        return auditFilePath;
    }

    public String getStructuredEventsTopic() {
        return structuredEventsTopic;
    }

    private boolean isMockEnabled() {
        return enabledPlatforms.contains("MOCK") || enabledPlatforms.contains("");
    }

    @Bean
    public Map<String, Object> kafkaProducerConfigs() {
        Map<String, Object> props = new HashMap<>();

        if (isMockEnabled() && StringUtils.isEmpty(bootstrapServers)) {
            LOGGER.info("Configuring Kafka event sender for MOCK");
            bootstrapServers = embeddedKafkaHost;
        }
        // list of host:port pairs used for establishing the initial connections to the Kakfa cluster
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        return props;
    }

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        return new DefaultKafkaProducerFactory<>(kafkaProducerConfigs());
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}