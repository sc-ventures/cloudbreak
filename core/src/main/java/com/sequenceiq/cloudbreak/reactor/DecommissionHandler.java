package com.sequenceiq.cloudbreak.reactor;

import static com.sequenceiq.cloudbreak.polling.PollingResult.isSuccess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.client.HttpClientConfig;
import com.sequenceiq.cloudbreak.core.bootstrap.service.host.HostOrchestratorResolver;
import com.sequenceiq.cloudbreak.domain.stack.Stack;
import com.sequenceiq.cloudbreak.domain.stack.cluster.host.HostGroup;
import com.sequenceiq.cloudbreak.domain.stack.cluster.host.HostMetadata;
import com.sequenceiq.cloudbreak.domain.stack.instance.InstanceMetaData;
import com.sequenceiq.cloudbreak.orchestrator.host.HostOrchestrator;
import com.sequenceiq.cloudbreak.orchestrator.model.GatewayConfig;
import com.sequenceiq.cloudbreak.reactor.api.event.EventSelectorUtil;
import com.sequenceiq.cloudbreak.reactor.api.event.resource.DecommissionRequest;
import com.sequenceiq.cloudbreak.reactor.api.event.resource.DecommissionResult;
import com.sequenceiq.cloudbreak.reactor.handler.ReactorEventHandler;
import com.sequenceiq.cloudbreak.repository.HostMetadataRepository;
import com.sequenceiq.cloudbreak.service.GatewayConfigService;
import com.sequenceiq.cloudbreak.polling.PollingResult;
import com.sequenceiq.cloudbreak.service.TlsSecurityService;
import com.sequenceiq.cloudbreak.ambari.AmbariDecommissioner;
import com.sequenceiq.cloudbreak.service.cluster.flow.recipe.RecipeEngine;
import com.sequenceiq.cloudbreak.service.hostgroup.HostGroupService;
import com.sequenceiq.cloudbreak.service.stack.StackService;

import reactor.bus.Event;
import reactor.bus.EventBus;

@Component
public class DecommissionHandler implements ReactorEventHandler<DecommissionRequest> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DecommissionHandler.class);

    @Inject
    private EventBus eventBus;

    @Inject
    private StackService stackService;

    @Inject
    private AmbariDecommissioner ambariDecommissioner;

    @Inject
    private RecipeEngine recipeEngine;

    @Inject
    private HostGroupService hostGroupService;

    @Inject
    private HostOrchestratorResolver hostOrchestratorResolver;

    @Inject
    private GatewayConfigService gatewayConfigService;

    @Inject
    private HostMetadataRepository hostMetadataRepository;

    @Inject
    private TlsSecurityService tlsSecurityService;


    @Override
    public String selector() {
        return EventSelectorUtil.selector(DecommissionRequest.class);
    }

    @Override
    public void accept(Event<DecommissionRequest> event) {
        DecommissionRequest request = event.getData();
        DecommissionResult result;
        String hostGroupName = request.getHostGroupName();
        try {
            Stack stack = stackService.getByIdWithListsInTransaction(request.getStackId());
            HttpClientConfig clientConfig = tlsSecurityService.buildTLSClientConfigForPrimaryGateway(stack.getId(), stack.getCluster().getAmbariIp());
            Set<String> hostNames = getHostNamesForPrivateIds(request, stack);
            HostGroup hostGroup = hostGroupService.getByClusterIdAndName(stack.getCluster().getId(), hostGroupName);
            Map<String, HostMetadata> hostsToRemove = ambariDecommissioner.collectHostsToRemove(stack, hostGroup, hostNames, clientConfig);
            Set<String> decomissionedHostNames;
            if (skipAmbariDecomission(request, hostsToRemove)) {
                decomissionedHostNames = hostNames;
            } else {
                executePreTerminationRecipes(stack, request.getHostGroupName(), hostsToRemove.keySet());
                Set<HostMetadata> decomissionedHostMetadatas = ambariDecommissioner.decommissionAmbariNodes(stack, hostsToRemove, clientConfig);
                decomissionedHostMetadatas.forEach(hostMetadata -> hostMetadataRepository.delete(hostMetadata));
                decomissionedHostNames = decomissionedHostMetadatas.stream().map(HostMetadata::getHostName).collect(Collectors.toSet());
            }
            HostOrchestrator hostOrchestrator = hostOrchestratorResolver.get(stack.getOrchestrator().getType());
            List<GatewayConfig> allGatewayConfigs = gatewayConfigService.getAllGatewayConfigs(stack);
            PollingResult orchestratorRemovalPollingResult =
                    ambariDecommissioner.removeHostsFromOrchestrator(stack, new ArrayList<>(decomissionedHostNames), hostOrchestrator, allGatewayConfigs);
            if (!isSuccess(orchestratorRemovalPollingResult)) {
                LOGGER.debug("Can not remove hosts from orchestrator: {}", decomissionedHostNames);
            }
            result = new DecommissionResult(request, decomissionedHostNames);
        } catch (Exception e) {
            result = new DecommissionResult(e.getMessage(), e, request);
        }
        eventBus.notify(result.selector(), new Event<>(event.getHeaders(), result));
    }

    private boolean skipAmbariDecomission(DecommissionRequest request, Map<String, HostMetadata> hostsToRemove) {
        return hostsToRemove.isEmpty() || request.getDetails() != null && request.getDetails().isForced();
    }

    private Set<String> getHostNamesForPrivateIds(DecommissionRequest request, Stack stack) {
        return request.getPrivateIds().stream().map(privateId -> {
            Optional<InstanceMetaData> instanceMetadata = stackService.getInstanceMetadata(stack.getInstanceMetaDataAsList(), privateId);
            if (instanceMetadata.isPresent()) {
                return instanceMetadata.get().getDiscoveryFQDN();
            } else {
                return null;
            }
        }).filter(StringUtils::isNotEmpty).collect(Collectors.toSet());
    }

    private void executePreTerminationRecipes(Stack stack, String hostGroupName, Set<String> hostNames) {
        try {
            HostGroup hostGroup = hostGroupService.getByClusterIdAndName(stack.getCluster().getId(), hostGroupName);
            recipeEngine.executePreTerminationRecipes(stack, Collections.singleton(hostGroup), hostNames);
        } catch (Exception ex) {
            LOGGER.warn(ex.getLocalizedMessage(), ex);
        }
    }
}
