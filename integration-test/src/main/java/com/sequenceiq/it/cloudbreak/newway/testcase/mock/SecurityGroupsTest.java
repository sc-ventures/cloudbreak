package com.sequenceiq.it.cloudbreak.newway.testcase.mock;

import static com.sequenceiq.it.cloudbreak.newway.context.RunningParameter.key;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.ForbiddenException;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.sequenceiq.it.cloudbreak.newway.action.credential.CredentialTestAction;
import com.sequenceiq.it.cloudbreak.newway.action.securitygroup.PlatformSecurityGroupsTestAction;
import com.sequenceiq.it.cloudbreak.newway.context.MockedTestContext;
import com.sequenceiq.it.cloudbreak.newway.context.TestContext;
import com.sequenceiq.it.cloudbreak.newway.entity.credential.CredentialTestDto;
import com.sequenceiq.it.cloudbreak.newway.entity.securitygroup.PlatformSecurityGroupsTestDto;
import com.sequenceiq.it.cloudbreak.newway.testcase.AbstractIntegrationTest;

public class SecurityGroupsTest extends AbstractIntegrationTest {

    @BeforeMethod
    public void beforeMethod(Object[] data) {
        createDefaultUser((TestContext) data[0]);
    }

    @Test(dataProvider = TEST_CONTEXT_WITH_MOCK)
    public void testGetSecurityGroupsByCredentialName(MockedTestContext testContext) {
        String credentialName = getNameGenerator().getRandomNameForResource();
        testContext
                .given(CredentialTestDto.class)
                .withName(credentialName)
                .when(CredentialTestAction::create)
                .given(PlatformSecurityGroupsTestDto.class)
                .withCredentialName(credentialName)
                .when(PlatformSecurityGroupsTestAction::getSecurityGroups);
    }

    @Test(dataProvider = "contextWithCredentialNameAndException")
    public void testGetSecurityGroupsByCredentialNameWhenCredentialIsInvalid(MockedTestContext testContext, String credentialName, String exceptionKey,
            Class<Exception> exception) {
        testContext
                .given(PlatformSecurityGroupsTestDto.class)
                .withCredentialName(credentialName)
                .when(PlatformSecurityGroupsTestAction::getSecurityGroups, key(exceptionKey))
                .expect(exception, key(exceptionKey))
                .validate();
    }

    @DataProvider(name = "contextWithCredentialNameAndException")
    public Object[][] provideInvalidAttributes() {
        return new Object[][]{
                {getBean(MockedTestContext.class), "", "badRequest", BadRequestException.class},
                {getBean(MockedTestContext.class), null, "badRequest", BadRequestException.class},
                {getBean(MockedTestContext.class), "andNowForSomethingCompletelyDifferent", "forbidden", ForbiddenException.class}
        };
    }

}
