package com.sequenceiq.it.cloudbreak.newway.testcase;

import java.lang.reflect.Method;
import java.util.Map;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.testng.AbstractTestNGSpringContextTests;
import org.testng.ITestContext;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;

import com.sequenceiq.it.cloudbreak.newway.CredentialEntity;
import com.sequenceiq.it.cloudbreak.newway.ImageCatalog;
import com.sequenceiq.it.cloudbreak.newway.ImageCatalogEntity;
import com.sequenceiq.it.cloudbreak.newway.RandomNameCreator;
import com.sequenceiq.it.cloudbreak.newway.action.CredentialCreateAction;
import com.sequenceiq.it.cloudbreak.newway.action.ImageCatalogCreateIfNotExistsAction;
import com.sequenceiq.it.cloudbreak.newway.actor.Actor;
import com.sequenceiq.it.cloudbreak.newway.context.PurgeGarbageService;
import com.sequenceiq.it.cloudbreak.newway.context.TestContext;
import com.sequenceiq.it.config.IntegrationTestConfiguration;
import com.sequenceiq.it.config.StructuredEventKafkaListener;

@ContextConfiguration(classes = {IntegrationTestConfiguration.class, StructuredEventKafkaListener.class},
        initializers = ConfigFileApplicationContextInitializer.class)
public abstract class AbstractIntegrationTest extends AbstractTestNGSpringContextTests {

    public static final Map<String, String> STACK_DELETED = Map.of("status", "DELETE_COMPLETED");

    protected static final Map<String, String> STACK_AVAILABLE = Map.of("status", "AVAILABLE", "clusterStatus", "AVAILABLE");

    protected static final Map<String, String> STACK_FAILED = Map.of("status", "AVAILABLE", "clusterStatus", "CREATE_FAILED");

    protected static final String INIT_STATE = "INIT_STATE";

    protected static final String START_PROVISIONING_STATE = "START_PROVISIONING_STATE";

    protected static final String STACK_CREATION_FINISHED_STATE = "STACK_CREATION_FINISHED_STATE";

    protected static final String STARTING_AMBARI_SERVICES_STATE = "STARTING_AMBARI_SERVICES_STATE";

    protected static final String CLUSTER_CREATION_FINISHED_STATE = "CLUSTER_CREATION_FINISHED_STATE";

    protected static final String CLUSTER_CREATION_FAILED_STATE = "CLUSTER_CREATION_FAILED_STATE";

    protected static final String DOWNSCALE_FINISHED_STATE = "DOWNSCALE_FINISHED_STATE";

    protected static final String FINALIZE_UPSCALE_STATE = "FINALIZE_UPSCALE_STATE";

    protected static final String DOWNSCALE_FAILED_STATE = "CLUSTER_DOWNSCALE_FAILED_STATE";

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractIntegrationTest.class);

    @Value("${integrationtest.cleanup.cleanupBeforeStart:false}")
    private boolean cleanupBeforeStart;

    @Inject
    private RandomNameCreator nameGenerator;

    @Inject
    private PurgeGarbageService purgeGarbageService;

    @BeforeSuite
    public void beforeSuite(ITestContext testngContext) {
        MDC.put("testlabel", "init of " + getClass().getSimpleName());
    }

    @BeforeMethod
    public void beforeMethod(Method method) {
        MDC.put("testlabel", method.getDeclaringClass().getSimpleName() + '.' + method.getName());
    }

    @BeforeClass
    public void createSharedObjects() {
        String testClassName = getClass().getSimpleName();
        MDC.put("testlabel", testClassName);
        if (cleanupBeforeStart) {
            purgeGarbageService.purge();
        }
    }

    @AfterClass(alwaysRun = true)
    public void cleanSharedObjects() {

    }

    @AfterSuite
    public void afterSuite() {

    }

    @DataProvider
    public Object[][] testContext() {
        return new Object[][]{{applicationContext.getBean(TestContext.class)}};
    }

    public RandomNameCreator getNameGenerator() {
        return nameGenerator;
    }

    protected void createDefaultCredential(TestContext testContext) {
        testContext.given(CredentialEntity.class)
                .when(new CredentialCreateAction());
    }

    protected void createDefaultImageCatalog(TestContext testContext) {
        testContext
                .given(ImageCatalog.class)
                .when(new ImageCatalogCreateIfNotExistsAction())
                .when(ImageCatalogEntity::putSetDefaultByName);
    }

    protected void createDefaultUser(TestContext testContext) {
        testContext.as();
    }

    protected void createSecondUser(TestContext testContext) {
        testContext.as(Actor::secondUser);
    }
}
