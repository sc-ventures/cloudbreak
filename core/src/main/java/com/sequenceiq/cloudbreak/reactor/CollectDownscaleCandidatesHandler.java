package com.sequenceiq.cloudbreak.reactor;

import static com.sequenceiq.cloudbreak.api.endpoint.v4.common.Status.AVAILABLE;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.springframework.stereotype.Component;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.sequenceiq.cloudbreak.client.HttpClientConfig;
import com.sequenceiq.cloudbreak.core.flow2.stack.CloudbreakFlowMessageService;
import com.sequenceiq.cloudbreak.message.Msg;
import com.sequenceiq.cloudbreak.domain.stack.cluster.host.HostGroup;
import com.sequenceiq.cloudbreak.domain.stack.cluster.host.HostMetadata;
import com.sequenceiq.cloudbreak.domain.stack.instance.InstanceMetaData;
import com.sequenceiq.cloudbreak.domain.stack.Stack;
import com.sequenceiq.cloudbreak.reactor.api.event.EventSelectorUtil;
import com.sequenceiq.cloudbreak.reactor.api.event.resource.CollectDownscaleCandidatesRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.resource.CollectDownscaleCandidatesResult;
import com.sequenceiq.cloudbreak.reactor.handler.ReactorEventHandler;
import com.sequenceiq.cloudbreak.service.CloudbreakException;
import com.sequenceiq.cloudbreak.service.TlsSecurityService;
import com.sequenceiq.cloudbreak.ambari.AmbariDecommissioner;
import com.sequenceiq.cloudbreak.service.hostgroup.HostGroupService;
import com.sequenceiq.cloudbreak.service.stack.DefaultRootVolumeSizeProvider;
import com.sequenceiq.cloudbreak.service.stack.StackService;

import reactor.bus.Event;
import reactor.bus.EventBus;

@Component
public class CollectDownscaleCandidatesHandler implements ReactorEventHandler<CollectDownscaleCandidatesRequest> {

    @Inject
    private EventBus eventBus;

    @Inject
    private StackService stackService;

    @Inject
    private AmbariDecommissioner ambariDecommissioner;

    @Inject
    private CloudbreakFlowMessageService flowMessageService;

    @Inject
    private HostGroupService hostGroupService;

    @Inject
    private DefaultRootVolumeSizeProvider defaultRootVolumeSizeProvider;

    @Inject
    private TlsSecurityService tlsSecurityService;

    @Override
    public String selector() {
        return EventSelectorUtil.selector(CollectDownscaleCandidatesRequest.class);
    }

    @Override
    public void accept(Event<CollectDownscaleCandidatesRequest> event) {
        CollectDownscaleCandidatesRequest request = event.getData();
        CollectDownscaleCandidatesResult result;
        try {
            Stack stack = stackService.getByIdWithListsInTransaction(request.getStackId());
            int defaultRootVolumeSize = defaultRootVolumeSizeProvider.getForPlatform(stack.cloudPlatform());
            HttpClientConfig clientConfig = tlsSecurityService.buildTLSClientConfigForPrimaryGateway(stack.getId(), stack.getCluster().getAmbariIp());
            Set<Long> privateIds = request.getPrivateIds();
            if (noSelectedInstancesForDownscale(privateIds)) {
                privateIds = collectCandidates(request, stack, clientConfig, defaultRootVolumeSize);
            } else {
                List<InstanceMetaData> instanceMetaDataList = stackService.getInstanceMetaDataForPrivateIds(stack.getInstanceMetaDataAsList(), privateIds);
                List<InstanceMetaData> notDeletedNodes = instanceMetaDataList.stream()
                        .filter(instanceMetaData -> !instanceMetaData.isTerminated() && !instanceMetaData.isDeletedOnProvider())
                        .collect(Collectors.toList());
                if (!request.getDetails().isForced()) {
                    Set<HostGroup> hostGroups = hostGroupService.getByCluster(stack.getCluster().getId());
                    Multimap<Long, HostMetadata> hostGroupWithInstances = getHostGroupWithInstances(stack, instanceMetaDataList);
                    ambariDecommissioner.verifyNodesAreRemovable(stack, hostGroupWithInstances, hostGroups, clientConfig, defaultRootVolumeSize);
                }
            }
            result = new CollectDownscaleCandidatesResult(request, privateIds);
        } catch (Exception e) {
            result = new CollectDownscaleCandidatesResult(e.getMessage(), e, request);
        }
        eventBus.notify(result.selector(), new Event<>(event.getHeaders(), result));
    }

    private Multimap<Long, HostMetadata> getHostGroupWithInstances(Stack stack, List<InstanceMetaData> instanceMetaDataList) {
        List<InstanceMetaData> instancesWithHostName = instanceMetaDataList.stream()
                .filter(instanceMetaData -> instanceMetaData.getDiscoveryFQDN() != null)
                .collect(Collectors.toList());

        Multimap<Long, HostMetadata> hostGroupWithInstances = ArrayListMultimap.create();
        for (InstanceMetaData instanceMetaData : instancesWithHostName) {
            HostMetadata hostMetadata = hostGroupService.getHostMetadataByClusterAndHostName(stack.getCluster(), instanceMetaData.getDiscoveryFQDN());
            if (hostMetadata != null) {
                hostGroupWithInstances.put(hostMetadata.getHostGroup().getId(), hostMetadata);
            }
        }
        return hostGroupWithInstances;
    }

    private Set<Long> collectCandidates(CollectDownscaleCandidatesRequest request, Stack stack, HttpClientConfig clientConfig, int defaultRootVolumeSize)
            throws CloudbreakException {
        HostGroup hostGroup = hostGroupService.getByClusterIdAndName(stack.getCluster().getId(), request.getHostGroupName());
        Set<String> hostNames = ambariDecommissioner.collectDownscaleCandidates(stack, hostGroup, request.getScalingAdjustment(), clientConfig,
                defaultRootVolumeSize);
        flowMessageService.fireEventAndLog(stack.getId(), Msg.STACK_SELECT_FOR_DOWNSCALE, AVAILABLE.name(), hostNames);
        return stackService.getPrivateIdsForHostNames(stack.getInstanceMetaDataAsList(), hostNames);
    }

    private boolean noSelectedInstancesForDownscale(Set<Long> privateIds) {
        return privateIds == null || privateIds.isEmpty();
    }
}
