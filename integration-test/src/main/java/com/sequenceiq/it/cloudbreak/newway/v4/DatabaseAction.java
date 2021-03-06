package com.sequenceiq.it.cloudbreak.newway.v4;

import static com.sequenceiq.it.cloudbreak.newway.log.Log.logJSON;

import java.io.IOException;
import java.util.Set;

import com.sequenceiq.cloudbreak.api.endpoint.v4.database.responses.DatabaseV4Response;
import com.sequenceiq.it.IntegrationTestContext;
import com.sequenceiq.it.cloudbreak.newway.CloudbreakClient;
import com.sequenceiq.it.cloudbreak.newway.CloudbreakTest;
import com.sequenceiq.it.cloudbreak.newway.entity.database.DatabaseEntity;
import com.sequenceiq.it.cloudbreak.newway.Entity;

public class DatabaseAction {
    private DatabaseAction() {
    }

    public static void post(IntegrationTestContext integrationTestContext, Entity entity) throws Exception {
        DatabaseEntity rdsconfigEntity = (DatabaseEntity) entity;
        CloudbreakClient client;
        client = integrationTestContext.getContextParam(CloudbreakClient.CLOUDBREAK_CLIENT, CloudbreakClient.class);
        Long workspaceId = integrationTestContext.getContextParam(CloudbreakTest.WORKSPACE_ID, Long.class);
        rdsconfigEntity.setResponse(
                client.getCloudbreakClient()
                        .databaseV4Endpoint()
                        .create(workspaceId, rdsconfigEntity.getRequest()));
        logJSON("Rds config post request: ", rdsconfigEntity.getRequest());
    }

    public static void get(IntegrationTestContext integrationTestContext, Entity entity) throws IOException {
        DatabaseEntity rdsconfigEntity = (DatabaseEntity) entity;
        CloudbreakClient client;
        client = integrationTestContext.getContextParam(CloudbreakClient.CLOUDBREAK_CLIENT, CloudbreakClient.class);
        Long workspaceId = integrationTestContext.getContextParam(CloudbreakTest.WORKSPACE_ID, Long.class);
        rdsconfigEntity.setResponse(
                client.getCloudbreakClient()
                        .databaseV4Endpoint()
                        .get(workspaceId, rdsconfigEntity.getRequest().getName()));
        logJSON(" get rds config response: ", rdsconfigEntity.getResponse());
    }

    public static void getAll(IntegrationTestContext integrationTestContext, Entity entity) throws IOException {
        DatabaseEntity rdsconfigEntity = (DatabaseEntity) entity;
        CloudbreakClient client;
        client = integrationTestContext.getContextParam(CloudbreakClient.CLOUDBREAK_CLIENT, CloudbreakClient.class);
        Long workspaceId = integrationTestContext.getContextParam(CloudbreakTest.WORKSPACE_ID, Long.class);
        rdsconfigEntity.setResponses(
                (Set<DatabaseV4Response>) client.getCloudbreakClient()
                        .databaseV4Endpoint()
                        .list(workspaceId, null, Boolean.FALSE)
                        .getResponses());
        logJSON(" get all rds config response: ", rdsconfigEntity.getResponse());
    }

    public static void delete(IntegrationTestContext integrationTestContext, Entity entity) {
        DatabaseEntity rdsconfigEntity = (DatabaseEntity) entity;
        CloudbreakClient client;
        client = integrationTestContext.getContextParam(CloudbreakClient.CLOUDBREAK_CLIENT, CloudbreakClient.class);
        Long workspaceId = integrationTestContext.getContextParam(CloudbreakTest.WORKSPACE_ID, Long.class);
        client.getCloudbreakClient()
                .databaseV4Endpoint()
                .delete(workspaceId, rdsconfigEntity.getName());
    }

    public static void createInGiven(IntegrationTestContext integrationTestContext, Entity entity) throws Exception {
        try {
            get(integrationTestContext, entity);
        } catch (Exception e) {
            post(integrationTestContext, entity);
        }
    }
}
