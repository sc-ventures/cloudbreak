package com.sequenceiq.it.cloudbreak.newway.testcase.mock;

import static com.sequenceiq.it.cloudbreak.newway.context.RunningParameter.expectedMessage;
import static com.sequenceiq.it.cloudbreak.newway.context.RunningParameter.key;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import javax.ws.rs.BadRequestException;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.sequenceiq.cloudbreak.api.endpoint.v4.clusterdefinition.responses.ClusterDefinitionV4ViewResponse;
import com.sequenceiq.cloudbreak.api.endpoint.v4.common.ResourceStatus;
import com.sequenceiq.it.cloudbreak.exception.TestFailException;
import com.sequenceiq.it.cloudbreak.newway.CloudbreakClient;
import com.sequenceiq.it.cloudbreak.newway.context.TestContext;
import com.sequenceiq.it.cloudbreak.newway.entity.clusterdefinition.ClusterDefinition;
import com.sequenceiq.it.cloudbreak.newway.entity.clusterdefinition.ClusterDefinitionEntity;
import com.sequenceiq.it.cloudbreak.newway.testcase.AbstractIntegrationTest;

public class ClusterDefintionTest extends AbstractIntegrationTest {

    private static final String VALID_BP = "{\"Blueprints\":{\"blueprint_name\":\"ownbp\",\"stack_name\":\"HDP\",\"stack_version\":\"2.6\"},\"settings\""
            + ":[{\"recovery_settings\":[]},{\"service_settings\":[]},{\"component_settings\":[]}],\"configurations\":[],\"host_groups\":[{\"name\":\"master\""
            + ",\"configurations\":[],\"components\":[{\"name\":\"HIVE_METASTORE\"}],\"cardinality\":\"1"
            + "\"}]}";

    @Override
    protected void minimalSetupForClusterCreation(TestContext testContext) {
        createDefaultUser(testContext);
    }

    @BeforeMethod
    public void beforeMethod(Object[] data) {
        TestContext testContext = (TestContext) data[0];
        minimalSetupForClusterCreation(testContext);
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(Object[] data) {
        ((TestContext) data[0]).cleanupTestContextEntity();
    }

    @Test(dataProvider = TEST_CONTEXT)
    public void testCreateBlueprint(TestContext testContext) {
        String blueprintName = getNameGenerator().getRandomNameForResource();
        List<String> keys = Arrays.asList("key_1", "key_2", "key_3");
        List<Object> values = Arrays.asList("value_1", "value_2", "value_3");
        testContext.given(ClusterDefinitionEntity.class)
                .withName(blueprintName)
                .withDescription(blueprintName)
                .withTag(keys, values)
                .withClusterDefinition(VALID_BP)
                .when(ClusterDefinition.postV4(), key(blueprintName))
                .then((tc, entity, cc) -> {
                    assertEquals(blueprintName, entity.getName());
                    assertEquals(blueprintName, entity.getDescription());
                    assertTrue(assertList(entity.getTag().keySet(), keys));
                    assertTrue(assertList(entity.getTag().values(), values));
                    return entity;
                })
                .validate();
    }

    @Test(dataProvider = TEST_CONTEXT)
    public void testCreateBlueprintWithInvalidCharacterName(TestContext testContext) {
        String blueprintName = getNameGenerator().getInvalidRandomNameForResource();
        testContext.given(ClusterDefinitionEntity.class)
                .withName(blueprintName)
                .withClusterDefinition(VALID_BP)
                .when(ClusterDefinition.postV4(), key(blueprintName))
                .expect(BadRequestException.class, expectedMessage(" error: must match ").withKey(blueprintName))
                .validate();
    }

    @Test(dataProvider = TEST_CONTEXT)
    public void testCreateBlueprintWithInvalidJson(TestContext testContext) {
        String blueprintName = getNameGenerator().getRandomNameForResource();
        testContext.given(ClusterDefinitionEntity.class)
                .withName(blueprintName)
                .withClusterDefinition("apple-tree")
                .when(ClusterDefinition.postV4(), key(blueprintName))
                .expect(BadRequestException.class, expectedMessage("Failed to parse JSON").withKey(blueprintName))
                .validate();
    }

    @Test(dataProvider = TEST_CONTEXT)
    public void testListBlueprint(TestContext testContext) {
        testContext.given(ClusterDefinitionEntity.class)
                .when(ClusterDefinition.listV4())
                .then(ClusterDefintionTest::checkDefaultBlueprintsIsListed)
                .validate();
    }

    @Test(dataProvider = TEST_CONTEXT_WITH_MOCK)
    public void testGetSpecificBlueprint(TestContext testContext) {
        String blueprintName = getNameGenerator().getRandomNameForResource();
        testContext.given(ClusterDefinitionEntity.class)
                .withName(blueprintName)
                .withClusterDefinition(VALID_BP)
                .when(ClusterDefinition.postV4(), key(blueprintName))
                .when(ClusterDefinition.getV4(), key(blueprintName))
                .then((tc, entity, cc) -> {
                    assertEquals(blueprintName, entity.getName());
                    return entity;
                })
                .validate();
    }

    @Test(dataProvider = TEST_CONTEXT)
    public void testDeleteSpecificBlueprint(TestContext testContext) {
        String blueprintName = getNameGenerator().getRandomNameForResource();
        testContext.given(ClusterDefinitionEntity.class)
                .withName(blueprintName)
                .withClusterDefinition(VALID_BP)
                .when(ClusterDefinition.postV4(), key(blueprintName))
                .when(ClusterDefinition.deleteV4(), key(blueprintName))
                .then((tc, entity, cc) -> {
                    assertEquals(blueprintName, entity.getName());
                    return entity;
                })
                .when(ClusterDefinition.listV4())
                .then(ClusterDefintionTest::checkBlueprintDoesNotExistInTheList)
                .validate();
    }

    private static ClusterDefinitionEntity checkBlueprintDoesNotExistInTheList(TestContext testContext,
        ClusterDefinitionEntity entity, CloudbreakClient cloudbreakClient) {
        if (entity.getViewResponses().stream().anyMatch(bp -> bp.getName().equals(entity.getName()))) {
            throw new TestFailException(
                    String.format("Blueprint is still exist in the db %s", entity.getName()));
        }
        return entity;
    }

    @Test(dataProvider = TEST_CONTEXT)
    public void testRequestSpecificBlueprintRequest(TestContext testContext) {
        String blueprintName = getNameGenerator().getRandomNameForResource();
        testContext.given(ClusterDefinitionEntity.class)
                .withName(blueprintName)
                .withClusterDefinition(VALID_BP)
                .when(ClusterDefinition.postV4(), key(blueprintName))
                .when(ClusterDefinition.requestV4(), key(blueprintName))
                .then((tc, entity, cc) -> {
                    assertEquals(entity.getRequest().getClusterDefinition(), VALID_BP);
                    assertEquals(blueprintName, entity.getName());
                    return entity;
                })
                .validate();
    }

    private static ClusterDefinitionEntity checkDefaultBlueprintsIsListed(TestContext testContext,
        ClusterDefinitionEntity blueprint, CloudbreakClient cloudbreakClient) {
        List<ClusterDefinitionV4ViewResponse> result = blueprint.getViewResponses().stream()
                .filter(bp -> bp.getStatus().equals(ResourceStatus.DEFAULT))
                .collect(Collectors.toList());
        if (result.isEmpty()) {
            throw new TestFailException("Cluster Definition is not listed");
        }
        return blueprint;
    }

    private  <O extends Object> boolean assertList(Collection<O> result, Collection<O> expected) {
        return result.containsAll(expected) && result.size() == expected.size();
    }
}
