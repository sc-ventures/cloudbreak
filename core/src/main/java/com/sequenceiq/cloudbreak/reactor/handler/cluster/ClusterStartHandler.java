package com.sequenceiq.cloudbreak.reactor.handler.cluster;

import javax.inject.Inject;

import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.domain.stack.Stack;
import com.sequenceiq.cloudbreak.reactor.api.event.EventSelectorUtil;
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.ClusterStartRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.cluster.ClusterStartResult;
import com.sequenceiq.cloudbreak.reactor.handler.ReactorEventHandler;
import com.sequenceiq.cloudbreak.service.cluster.ClusterApiConnectors;
import com.sequenceiq.cloudbreak.service.stack.StackService;

import reactor.bus.Event;
import reactor.bus.EventBus;

@Component
public class ClusterStartHandler implements ReactorEventHandler<ClusterStartRequest> {
    @Inject
    private ClusterApiConnectors apiConnectors;

    @Inject
    private StackService stackService;

    @Inject
    private EventBus eventBus;

    @Override
    public String selector() {
        return EventSelectorUtil.selector(ClusterStartRequest.class);
    }

    @Override
    public void accept(Event<ClusterStartRequest> event) {
        ClusterStartRequest request = event.getData();
        ClusterStartResult result;
        try {
            Stack stack = stackService.getByIdWithListsInTransaction(request.getStackId());
            int requestId = apiConnectors.getConnector(stack.getCluster().getVariant()).startCluster(stack);
            result = new ClusterStartResult(request, requestId);
        } catch (Exception e) {
            result = new ClusterStartResult(e.getMessage(), e, request);
        }
        eventBus.notify(result.selector(), new Event<>(event.getHeaders(), result));
    }
}
