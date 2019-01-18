package com.sequenceiq.it.config;


import java.io.IOException;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;

import com.sequenceiq.cloudbreak.structuredevent.event.StructuredFlowEvent;
import com.sequenceiq.cloudbreak.util.JsonUtil;
import com.sequenceiq.it.StructuredEventAwaiter;

public class StructuredEventKafkaListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(StructuredEventKafkaListener.class);

    @Inject
    private StructuredEventAwaiter awaiter;

    @KafkaListener(topics = "StructuredEvents", containerFactory = "kafkaListenerContainerFactory")
    public void receiveStructureEvent(final String payload) {
        StructuredFlowEvent flowEvent = parseStructuredFlowEvent(payload);
        if (flowEvent.getFlow() != null) {
            LOGGER.info("CLUSTER  ({}) STATUS: [{}]", flowEvent.getOperation().getResourceName(), flowEvent.getFlow().getFlowState());
            checkAwaitedStatuses(flowEvent);
        }
    }

    public StructuredFlowEvent parseStructuredFlowEvent(String payload) {
        try {
            return JsonUtil.readValue(payload, StructuredFlowEvent.class);
        } catch (IOException e) {
            LOGGER.debug("Parse not successful for payload: {}", payload);
        }
        return null;
    }

    private void checkAwaitedStatuses(StructuredFlowEvent event) {
        awaiter.getAwaiters().values().stream()
                .filter(awaiter -> awaiter.getClusterName().equals(event.getOperation().getResourceName()))
                .filter(awaiter -> awaiter.getClusterStatus().equals(event.getFlow().getFlowState()))
                .forEach(awaiter -> {
                    awaiter.getLatch().countDown();
                    LOGGER.info("Awaited event arrived");
                });
    }
}