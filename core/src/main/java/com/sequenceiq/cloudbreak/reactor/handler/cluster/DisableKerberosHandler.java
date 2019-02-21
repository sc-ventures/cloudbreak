package com.sequenceiq.cloudbreak.reactor.handler.cluster;

import javax.inject.Inject;

import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.reactor.api.event.EventSelectorUtil;
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.DisableKerberosRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.DisableKerberosResult;
import com.sequenceiq.cloudbreak.reactor.handler.ReactorEventHandler;
import com.sequenceiq.cloudbreak.ambari.AmbariClusterConnector;
import com.sequenceiq.cloudbreak.service.stack.StackService;

import reactor.bus.Event;
import reactor.bus.EventBus;

@Component
public class DisableKerberosHandler implements ReactorEventHandler<DisableKerberosRequest> {

    @Inject
    private EventBus eventBus;

    @Inject
    private AmbariClusterConnector ambariClusterConnector;

    @Inject
    private StackService stackService;

    @Override
    public String selector() {
        return EventSelectorUtil.selector(DisableKerberosRequest.class);
    }

    @Override
    public void accept(Event<DisableKerberosRequest> event) {
        DisableKerberosResult result;
        try {
            ambariClusterConnector.disableSecurity(stackService.getByIdWithListsInTransaction(event.getData().getStackId()));
            result = new DisableKerberosResult(event.getData());
        } catch (Exception e) {
            result = new DisableKerberosResult(e.getMessage(), e, event.getData());
        }
        eventBus.notify(result.selector(), new Event<>(event.getHeaders(), result));
    }
}
