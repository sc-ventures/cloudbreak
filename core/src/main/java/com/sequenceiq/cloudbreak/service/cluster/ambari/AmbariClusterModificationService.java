package com.sequenceiq.cloudbreak.service.cluster.ambari;

import static com.sequenceiq.cloudbreak.api.endpoint.v4.common.Status.UPDATE_IN_PROGRESS;
import static com.sequenceiq.cloudbreak.service.PollingResult.isExited;
import static com.sequenceiq.cloudbreak.service.PollingResult.isTimeout;
import static com.sequenceiq.cloudbreak.service.cluster.ambari.AmbariMessages.AMBARI_CLUSTER_INSTALL_FAILED;
import static com.sequenceiq.cloudbreak.service.cluster.ambari.AmbariMessages.AMBARI_CLUSTER_SERVICES_INIT_FAILED;
import static com.sequenceiq.cloudbreak.service.cluster.ambari.AmbariMessages.AMBARI_CLUSTER_SERVICES_STARTING;
import static com.sequenceiq.cloudbreak.service.cluster.ambari.AmbariMessages.AMBARI_CLUSTER_SERVICES_START_FAILED;
import static com.sequenceiq.cloudbreak.service.cluster.ambari.AmbariMessages.AMBARI_CLUSTER_SERVICES_STOPPED;
import static com.sequenceiq.cloudbreak.service.cluster.ambari.AmbariMessages.AMBARI_CLUSTER_SERVICES_STOPPING;
import static com.sequenceiq.cloudbreak.service.cluster.ambari.AmbariMessages.AMBARI_CLUSTER_SERVICES_STOP_FAILED;
import static com.sequenceiq.cloudbreak.service.cluster.ambari.AmbariMessages.AMBARI_CLUSTER_UPSCALE_FAILED;
import static com.sequenceiq.cloudbreak.service.cluster.ambari.AmbariOperationType.INIT_SERVICES_AMBARI_PROGRESS_STATE;
import static com.sequenceiq.cloudbreak.service.cluster.ambari.AmbariOperationType.INSTALL_SERVICES_AMBARI_PROGRESS_STATE;
import static com.sequenceiq.cloudbreak.service.cluster.ambari.AmbariOperationType.START_SERVICES_AMBARI_PROGRESS_STATE;
import static com.sequenceiq.cloudbreak.service.cluster.ambari.AmbariOperationType.STOP_AMBARI_PROGRESS_STATE;
import static com.sequenceiq.cloudbreak.service.cluster.ambari.AmbariOperationType.UPSCALE_AMBARI_PROGRESS_STATE;
import static com.sequenceiq.cloudbreak.service.cluster.ambari.HostGroupAssociationBuilder.FQDN;
import static java.util.Collections.singletonMap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.google.common.collect.Sets;
import com.sequenceiq.ambari.client.AmbariClient;
import com.sequenceiq.ambari.client.AmbariConnectionException;
import com.sequenceiq.cloudbreak.cloud.retry.RetryUtil;
import com.sequenceiq.cloudbreak.cloud.scheduler.CancellationException;
import com.sequenceiq.cloudbreak.controller.exception.BadRequestException;
import com.sequenceiq.cloudbreak.core.ClusterException;
import com.sequenceiq.cloudbreak.domain.KerberosConfig;
import com.sequenceiq.cloudbreak.domain.stack.Stack;
import com.sequenceiq.cloudbreak.domain.stack.cluster.host.HostGroup;
import com.sequenceiq.cloudbreak.domain.stack.cluster.host.HostMetadata;
import com.sequenceiq.cloudbreak.repository.HostMetadataRepository;
import com.sequenceiq.cloudbreak.service.CloudbreakException;
import com.sequenceiq.cloudbreak.service.CloudbreakServiceException;
import com.sequenceiq.cloudbreak.service.PollingResult;
import com.sequenceiq.cloudbreak.service.Retry;
import com.sequenceiq.cloudbreak.service.cluster.ClusterConnectorPollingResultChecker;
import com.sequenceiq.cloudbreak.service.cluster.api.ClusterModificationService;
import com.sequenceiq.cloudbreak.service.cluster.flow.AmbariOperationService;
import com.sequenceiq.cloudbreak.service.cluster.flow.recipe.RecipeEngine;
import com.sequenceiq.cloudbreak.service.events.CloudbreakEventService;
import com.sequenceiq.cloudbreak.service.messages.CloudbreakMessagesService;
import com.sequenceiq.cloudbreak.util.AmbariClientExceptionUtil;

import groovyx.net.http.HttpResponseException;

@Service
public class AmbariClusterModificationService implements ClusterModificationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AmbariClusterModificationService.class);

    private static final String STATE_INSTALLED = "INSTALLED";

    @Inject
    private AmbariClientFactory clientFactory;

    @Inject
    private ClusterConnectorPollingResultChecker clusterConnectorPollingResultChecker;

    @Inject
    private AmbariOperationService ambariOperationService;

    @Inject
    private RecipeEngine recipeEngine;

    @Inject
    private HostMetadataRepository hostMetadataRepository;

    @Inject
    private HostGroupAssociationBuilder hostGroupAssociationBuilder;

    @Inject
    private CloudbreakMessagesService cloudbreakMessagesService;

    @Inject
    private CloudbreakEventService eventService;

    @Inject
    private AmbariPollingServiceProvider ambariPollingServiceProvider;

    @Inject
    private Retry retry;

    @Override
    public void upscaleCluster(Stack stack, HostGroup hostGroup, Collection<HostMetadata> hostMetadata) throws CloudbreakException {
        AmbariClient ambariClient = clientFactory.getAmbariClient(stack, stack.getCluster());
        List<String> upscaleHostNames = hostMetadata
                .stream()
                .map(HostMetadata::getHostName)
                .collect(Collectors.toList())
                .stream()
                .filter(hostName -> !ambariClient.getClusterHosts().contains(hostName))
                .collect(Collectors.toList());
        if (!upscaleHostNames.isEmpty()) {
            recipeEngine.executePostAmbariStartRecipes(stack, Sets.newHashSet(hostGroup));
            Pair<PollingResult, Exception> pollingResult = ambariOperationService.waitForOperations(
                    stack,
                    ambariClient,
                    installServices(upscaleHostNames, stack, ambariClient, hostGroup),
                    UPSCALE_AMBARI_PROGRESS_STATE);
            String message = pollingResult.getRight() == null
                    ? cloudbreakMessagesService.getMessage(AMBARI_CLUSTER_UPSCALE_FAILED.code())
                    : pollingResult.getRight().getMessage();
            clusterConnectorPollingResultChecker.checkPollingResult(pollingResult.getLeft(), message);
        }
    }

    @Override
    public void stopCluster(Stack stack) throws CloudbreakException {
        AmbariClient ambariClient = clientFactory.getAmbariClient(stack, stack.getCluster());
        try {
            boolean stopped = true;
            Collection<Map<String, String>> values = ambariClient.getHostComponentsStates().values();
            for (Map<String, String> value : values) {
                for (String state : value.values()) {
                    if (!"INSTALLED".equals(state)) {
                        stopped = false;
                    }
                }
            }
            if (!stopped) {
                LOGGER.debug("Stop all Hadoop services");
                eventService
                        .fireCloudbreakEvent(stack.getId(), UPDATE_IN_PROGRESS.name(),
                                cloudbreakMessagesService.getMessage(AMBARI_CLUSTER_SERVICES_STOPPING.code()));
                int requestId = ambariClient.stopAllServices();
                if (requestId != -1) {
                    LOGGER.debug("Waiting for Hadoop services to stop on stack");
                    PollingResult servicesStopResult = ambariOperationService.waitForOperations(stack, ambariClient, singletonMap("stop services", requestId),
                            STOP_AMBARI_PROGRESS_STATE).getLeft();
                    if (isExited(servicesStopResult)) {
                        throw new CancellationException("Cluster was terminated while waiting for Hadoop services to start");
                    } else if (isTimeout(servicesStopResult)) {
                        throw new CloudbreakException("Timeout while stopping Ambari services.");
                    }
                } else {
                    LOGGER.debug("Failed to stop Hadoop services.");
                    throw new CloudbreakException("Failed to stop Hadoop services.");
                }
                eventService
                        .fireCloudbreakEvent(stack.getId(), UPDATE_IN_PROGRESS.name(),
                                cloudbreakMessagesService.getMessage(AMBARI_CLUSTER_SERVICES_STOPPED.code()));
            }
        } catch (AmbariConnectionException ignored) {
            LOGGER.debug("Ambari not running on the gateway machine, no need to stop it.");
        }
    }

    @Override
    public int startCluster(Stack stack) throws CloudbreakException {
        AmbariClient ambariClient = clientFactory.getAmbariClient(stack, stack.getCluster());
        PollingResult ambariHealthCheckResult = ambariPollingServiceProvider.ambariHealthChecker(stack, ambariClient);
        if (isExited(ambariHealthCheckResult)) {
            throw new CancellationException("Cluster was terminated while waiting for Ambari to start.");
        } else if (isTimeout(ambariHealthCheckResult)) {
            throw new CloudbreakException("Ambari server was not restarted properly.");
        }
        LOGGER.debug("Starting Ambari agents on the hosts.");
        Set<HostMetadata> hostsInCluster = hostMetadataRepository.findHostsInCluster(stack.getCluster().getId());
        PollingResult hostsJoinedResult = ambariPollingServiceProvider.ambariHostJoin(stack, ambariClient, hostsInCluster);
        if (isExited(hostsJoinedResult)) {
            throw new CancellationException("Cluster was terminated while starting Ambari agents.");
        }

        LOGGER.debug("Start all Hadoop services");
        eventService
                .fireCloudbreakEvent(stack.getId(), UPDATE_IN_PROGRESS.name(), cloudbreakMessagesService.getMessage(AMBARI_CLUSTER_SERVICES_STARTING.code()));
        int requestId = ambariClient.startAllServices();
        if (requestId == -1) {
            LOGGER.info("Failed to start Hadoop services.");
            throw new CloudbreakException("Failed to start Hadoop services.");
        }
        return requestId;
    }

    @Override
    public Map<String, String> gatherInstalledComponents(Stack stack, String hostname) {
        AmbariClient ambariClient = clientFactory.getAmbariClient(stack, stack.getCluster());
        Set<String> components = ambariClient.getHostComponentsMap(hostname).keySet();
        return ambariClient.getComponentsCategory(new ArrayList<>(components));
    }

    @Override
    public void stopComponents(Stack stack, Map<String, String> components, String hostname) throws CloudbreakException {
        stopComponentsInternal(stack, new ArrayList<>(components.keySet()), hostname, OperationParameters.DO_NOT_WAIT);
    }

    @Override
    public void ensureComponentsAreStopped(Stack stack, Map<String, String> components, String hostname) throws CloudbreakException {
        AmbariClient ambariClient = clientFactory.getAmbariClient(stack, stack.getCluster());
        Map<String, String> masterSlaveComponentsWithState = getMasterSlaveComponentStatuses(components, hostname, ambariClient);
        Map<String, String> componentsNotInDesiredState = masterSlaveComponentsWithState.entrySet().stream()
                .filter(e -> !STATE_INSTALLED.equals(e.getValue()))
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
        if (!componentsNotInDesiredState.isEmpty()) {
            LOGGER.info("Some components are not in {} state: {}, stopping them",
                    STATE_INSTALLED, componentsNotInDesiredStateToString(componentsNotInDesiredState));
            stopComponentsInternal(stack, new ArrayList<>(componentsNotInDesiredState.keySet()), hostname,
                    new OperationParameters(STOP_AMBARI_PROGRESS_STATE, AMBARI_CLUSTER_SERVICES_STOP_FAILED));
        }
    }

    @Override
    public void initComponents(Stack stack, Map<String, String> components, String hostname) throws CloudbreakException {
        AmbariClient ambariClient = clientFactory.getAmbariClient(stack, stack.getCluster());
        try {
            Map<String, Integer> operationRequests = ambariClient.initComponentsOnHost(hostname, collectMasterSlaveComponents(components));
            waitForOperation(stack, ambariClient, operationRequests, INIT_SERVICES_AMBARI_PROGRESS_STATE, AMBARI_CLUSTER_SERVICES_INIT_FAILED);
        } catch (RuntimeException | HttpResponseException e) {
            throw new CloudbreakException("Failed to init Hadoop services.", e);
        }
    }

    /**
     * Note: on ambari stopping and installing components is the same desired state: INSTALLED. If you want to install or resintall a component, you have to
     * 1) INSTALLED (if it is in STARTED or STARTING state
     * 2) INIT
     * 3) INSTALLED
     *
     * @param stack      The stack
     * @param components Map of components - componentType (MASTER, SLAVE, CLIENT)
     * @param hostname   The host of ambari
     * @throws CloudbreakException thrown in case of any exception
     */
    @Override
    public void installComponents(Stack stack, Map<String, String> components, String hostname) throws CloudbreakException {
        stopComponentsInternal(stack, collectMasterSlaveComponents(components), hostname,
                new OperationParameters(INSTALL_SERVICES_AMBARI_PROGRESS_STATE, AMBARI_CLUSTER_INSTALL_FAILED));
    }

    @Override
    public void regenerateKerberosKeytabs(Stack stack, String hostname) throws CloudbreakException {
        try {
            KerberosConfig kerberosConfig = stack.getCluster().getKerberosConfig();
            AmbariClient ambariClient = clientFactory.getAmbariClient(stack, stack.getCluster());
            LOGGER.info("Setting kerberos principal {} and password on master node {} ", kerberosConfig.getPrincipal(), hostname);
            ambariClient.setKerberosPrincipal(kerberosConfig.getPrincipal(), kerberosConfig.getPassword());
            LOGGER.info("Regenerating kerberos keytabs for missing nodes and services");
            Integer ambariTaskId = ambariClient.generateKeytabs(false);
            waitForOperation(stack, ambariClient, Map.of("KerberosRegenerateKeytabs", ambariTaskId), START_SERVICES_AMBARI_PROGRESS_STATE,
                    AMBARI_CLUSTER_SERVICES_START_FAILED);
        } catch (ClusterException e) {
            throw new CloudbreakException("Error regenerating keytabs on ambari", e);
        }
    }

    @Override
    public void startComponents(Stack stack, Map<String, String> components, String hostname) throws CloudbreakException {
        tryWithRetry(() -> {
            try {
                AmbariClient ambariClient = clientFactory.getAmbariClient(stack, stack.getCluster());
                Map<String, Integer> operationRequests = ambariClient.startComponentsOnHost(hostname, collectMasterSlaveComponents(components));
                waitForOperation(stack, ambariClient, operationRequests, START_SERVICES_AMBARI_PROGRESS_STATE, AMBARI_CLUSTER_SERVICES_START_FAILED);
            } catch (RuntimeException | HttpResponseException e) {
                LOGGER.error("Error starting components on ambari", e);
                throw new RecoverableAmbariException(e);
            } catch (ClusterException e) {
                LOGGER.error("Error starting components on ambari", e);
                if (PollingResult.isFailure(e.getPollingResult())) {
                    throw new RecoverableAmbariException(e);
                }
                throw new IrrecoverableAmbariException(e);
            }
        });
    }

    @Override
    public void restartAll(Stack stack) throws CloudbreakException {
        tryWithRetry(() -> {
            try {
                AmbariClient ambariClient = clientFactory.getAmbariClient(stack, stack.getCluster());
                Integer operationId = ambariClient.restartAllServices(stack.getCluster().getName());
                Map<String, Integer> operationRequests = Map.of("restartAllServices", operationId);
                waitForOperation(stack, ambariClient, operationRequests, START_SERVICES_AMBARI_PROGRESS_STATE, AMBARI_CLUSTER_SERVICES_START_FAILED);
            } catch (RuntimeException e) {
                LOGGER.error("Error starting components on ambari", e);
                throw new RecoverableAmbariException(e);
            } catch (ClusterException e) {
                LOGGER.error("Error starting components on ambari", e);
                if (PollingResult.isFailure(e.getPollingResult())) {
                    throw new RecoverableAmbariException(e);
                }
                throw new IrrecoverableAmbariException(e);
            }
        });
    }

    private Map<String, String> getMasterSlaveComponentStatuses(Map<String, String> components, String hostname, AmbariClient ambariClient)
            throws CloudbreakException {
        try {
            return retry.testWith2SecDelayMax15Times(() -> {
                Map<String, String> componentStatus = ambariClient.getHostComponentsMap(hostname);
                Map<String, String> masterSlaveWithState = collectMasterSlaveComponents(components).stream()
                        .collect(Collectors.toMap(Function.identity(), componentStatus::get));
                if (masterSlaveWithState.values().stream().anyMatch("UNKNOWN"::equals)) {
                    throw new Retry.ActionWentFailException("Ambari has not recovered");
                }
                return componentStatus;
            });
        } catch (Retry.ActionWentFailException e) {
            throw new CloudbreakException("Status of one or more components in ambari remained in UNKNOWN status.");
        }
    }

    private String componentsNotInDesiredStateToString(Map<String, String> componentsNotInDesiredState) {
        return componentsNotInDesiredState.entrySet().stream()
                .map(e -> String.format("[%s=>%s]", e.getKey(), e.getValue()))
                .collect(Collectors.joining(", "));
    }

    private List<String> collectMasterSlaveComponents(Map<String, String> components) {
        List<String> serviceCategories = Arrays.asList("MASTER", "SLAVE");
        return components.entrySet().stream()
                .filter(e -> serviceCategories.contains(e.getValue()))
                .map(Entry::getKey)
                .collect(Collectors.toList());
    }

    private void stopComponentsInternal(Stack stack, List<String> components, String hostname, OperationParameters operationParameters)
            throws CloudbreakException {
        tryWithRetry(() -> {
            try {
                AmbariClient ambariClient = clientFactory.getAmbariClient(stack, stack.getCluster());
                Map<String, Integer> operationRequests = ambariClient.stopComponentsOnHost(hostname, components);
                if (operationParameters.waitForOperation) {
                    waitForOperation(stack, ambariClient, operationRequests, operationParameters.getAmbariOperationType(),
                            operationParameters.getOperationFailedMessage());
                }
            } catch (RuntimeException | HttpResponseException e) {
                LOGGER.error("Error stopping components on ambari", e);
                throw new RecoverableAmbariException(e);
            } catch (ClusterException e) {
                LOGGER.error("Error stopping components on ambari", e);
                if (PollingResult.isFailure(e.getPollingResult())) {
                    throw new RecoverableAmbariException(e);
                }
                throw new IrrecoverableAmbariException(e);
            }
        });
    }

    private void waitForOperation(Stack stack, AmbariClient ambariClient, Map<String, Integer> operationRequests,
            AmbariOperationType type, AmbariMessages failureMessage) throws ClusterException {
        operationRequests = operationRequests.entrySet().stream()
                .filter(e -> e.getValue() != -1)
                .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
        if (operationRequests.isEmpty()) {
            return;
        }
        Pair<PollingResult, Exception> pollingResult = ambariOperationService.waitForOperations(stack, ambariClient, operationRequests, type);
        String message = pollingResult.getRight() == null
                ? cloudbreakMessagesService.getMessage(failureMessage.code())
                : pollingResult.getRight().getMessage();
        clusterConnectorPollingResultChecker.checkPollingResult(pollingResult.getLeft(), message);
    }

    private void tryWithRetry(Runnable action) throws CloudbreakException {
        Queue<CloudbreakException> errors = new ArrayDeque<>();
        RetryUtil.withDefaultRetries()
                .retry(action::run)
                .checkIfRecoverable(e -> e instanceof RecoverableAmbariException)
                .ifNotRecoverable(e -> errors.add(new CloudbreakException(e)))
                .run();

        if (!errors.isEmpty()) {
            throw errors.poll();
        }
    }

    private Map<String, Integer> installServices(List<String> hosts, Stack stack, AmbariClient ambariClient, HostGroup hostGroup) {
        try {
            String blueprintName = stack.getCluster().getClusterDefinition().getStackName();
            // In case If we changed the clusterDefinitionName field we need to query the validation name information from ambari
            Map<String, String> blueprintsMap = ambariClient.getBlueprintsMap();
            if (!blueprintsMap.entrySet().isEmpty()) {
                blueprintName = blueprintsMap.keySet().iterator().next();
            }
            List<Map<String, String>> hostGroupAssociation = hostGroupAssociationBuilder.buildHostGroupAssociation(hostGroup);
            Map<String, String> hostsWithRackInfo = hostGroupAssociation.stream()
                    .filter(associationMap -> hosts.stream().anyMatch(host -> host.equals(associationMap.get(FQDN))))
                    .collect(Collectors.toMap(association -> association.get(FQDN), association ->
                            association.get("rack") != null ? association.get("rack") : "/default-rack"));
            int upscaleRequestCode = ambariClient.addHostsAndRackInfoWithBlueprint(blueprintName, hostGroup.getName(), hostsWithRackInfo);
            return singletonMap("UPSCALE_REQUEST", upscaleRequestCode);
        } catch (HttpResponseException e) {
            if ("Conflict".equals(e.getMessage())) {
                throw new BadRequestException("Host already exists.", e);
            } else {
                String errorMessage = AmbariClientExceptionUtil.getErrorMessage(e);
                throw new CloudbreakServiceException("Ambari could not install services. " + errorMessage, e);
            }
        }
    }

    private static class RecoverableAmbariException extends RuntimeException {
        RecoverableAmbariException(Exception cause) {
            super(cause);
        }
    }

    private static class IrrecoverableAmbariException extends RuntimeException {
        IrrecoverableAmbariException(Exception cause) {
            super(cause);
        }
    }

    private static class OperationParameters {

        private static final OperationParameters DO_NOT_WAIT = new OperationParameters();

        private final boolean waitForOperation;

        private AmbariOperationType ambariOperationType;

        private AmbariMessages operationFailedMessage;

        private OperationParameters(AmbariOperationType ambariOperationType, AmbariMessages operationFailedMessage) {
            this.waitForOperation = true;
            this.ambariOperationType = ambariOperationType;
            this.operationFailedMessage = operationFailedMessage;
        }

        private OperationParameters() {
            this.waitForOperation = false;
        }

        private boolean isWaitForOperation() {
            return waitForOperation;
        }

        private AmbariOperationType getAmbariOperationType() {
            return ambariOperationType;
        }

        private AmbariMessages getOperationFailedMessage() {
            return operationFailedMessage;
        }
    }
}
