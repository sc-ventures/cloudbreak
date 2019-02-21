package com.sequenceiq.cloudbreak.service.cluster.clouderamanager;

import javax.inject.Inject;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import com.sequenceiq.cloudbreak.client.HttpClientConfig;
import com.sequenceiq.cloudbreak.cluster.api.ClusterApi;
import com.sequenceiq.cloudbreak.cluster.api.ClusterModificationService;
import com.sequenceiq.cloudbreak.cluster.api.ClusterSecurityService;
import com.sequenceiq.cloudbreak.cluster.api.ClusterSetupService;
import com.sequenceiq.cloudbreak.domain.stack.Stack;
import com.sequenceiq.cloudbreak.domain.stack.cluster.Cluster;

@Service(ClusterApi.CLOUDERA_MANAGER)
@Scope("prototype")
public class ClouderaManagerConnector implements ClusterApi {

    @Inject
    private ClouderaManagerSetupService clouderaManagerSetupService;

    @Inject
    private ClouderaManagerModificationService clouderaManagerModificationService;

    @Inject
    private ClouderaManagerSecurityService clouderaManagerSecurityService;

    private final Stack stack;

    private final HttpClientConfig clientConfig;

    public ClouderaManagerConnector(Stack stack, HttpClientConfig clientConfig) {
        this.stack = stack;
        this.clientConfig = clientConfig;
    }

    @Override
    public ClusterSetupService clusterSetupService() {
        return clouderaManagerSetupService;
    }

    @Override
    public ClusterModificationService clusterModificationService() {
        return clouderaManagerModificationService;
    }

    @Override
    public ClusterSecurityService clusterSecurityService() {
        return clouderaManagerSecurityService;
    }
}
