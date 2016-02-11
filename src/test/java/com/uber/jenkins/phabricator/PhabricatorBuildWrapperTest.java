// Copyright (c) 2015 Uber Technologies, Inc.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

package com.uber.jenkins.phabricator;

import com.uber.jenkins.phabricator.utils.TestUtils;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import net.sf.json.JSONObject;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

public class PhabricatorBuildWrapperTest extends BuildIntegrationTest {
    private PhabricatorBuildWrapper wrapper;

    @Before
    public void setUp() throws Exception {
        p = createProject();
        wrapper = new PhabricatorBuildWrapper(
                false,
                false,
                true,
                false,
                false,
                false
        );
        wrapper.getDescriptor().setArcPath("echo");
    }

    @Test
    public void testGetters() {
        assertFalse(wrapper.isCreateCommit());
        assertFalse(wrapper.isApplyToMaster());
        assertTrue(wrapper.isShowBuildStartedMessage());
        assertFalse(wrapper.isPatchWithForceFlag());
    }

    @Ignore("causes travis CI to crash")
    @Test
    public void testRoundTripConfiguration() throws Exception {
        addBuildStep();

        j.submit(j.createWebClient().getPage(p, "configure").getFormByName("config"));

        PhabricatorBuildWrapper after = p.getBuildWrappersList().get(PhabricatorBuildWrapper.class);
        j.assertEqualBeans(wrapper, after,
                "createCommit,applyToMaster,showBuildStartedMessage,createBranch");
    }

    @Test
    public void testNoParameterBuild() throws Exception {
        addBuildStep();

        FreeStyleBuild build = p.scheduleBuild2(0).get();
        Result result = build.getResult();
        assertSuccessfulBuild(result);
    }

    @Test
    public void testBuildNoConduit() throws Exception {
        addBuildStep();
        TestUtils.setDefaultBuildEnvironment(j);

        FreeStyleBuild build = p.scheduleBuild2(0).get();
        assertFailureWithMessage("No credentials configured", build);
    }

    @Test
    public void testBuildInvalidConduit() throws Exception {
        TestUtils.addInvalidCredentials();
        addBuildStep();
        TestUtils.setDefaultBuildEnvironment(j);

        FreeStyleBuild build = p.scheduleBuild2(0).get();
        assertFailureWithMessage("UnknownHostException", build);
    }

    @Test
    public void testBuildValidConduitEmptyResponse() throws Exception {
        FreeStyleBuild build = buildWithConduit(null, null, null, true);

        assertFailureWithMessage("Unable to fetch differential", build);
    }

    @Test
    public void testBuildValidErrorCommenting() throws Exception {
        FreeStyleBuild build = buildWithConduit(getFetchDiffResponse(), null, null, true);

        assertFailureWithMessage("Unable to fetch differential", build);
    }

    @Test
    public void testBuildValidSuccess() throws Exception {
        JSONObject commentResponse = new JSONObject();
        FreeStyleBuild build = buildWithConduit(getFetchDiffResponse(), commentResponse, null, true);

        assertEquals(Result.SUCCESS, build.getResult());
        PhabricatorPostbuildSummaryAction action = build.getAction(PhabricatorPostbuildSummaryAction.class);
        assertNotNull(action);
        assertEquals("sc@ndella.com", action.getAuthorEmail());
        assertEquals("aiden", action.getAuthorName());
        assertEquals("commit message", action.getCommitMessage());
        assertNotNull(action.getIconPath());
    }

    @Test
    public void testBuildValidWithoutHarbormaster() throws Exception {
        JSONObject commentResponse = new JSONObject();
        FreeStyleBuild build = buildWithConduit(getFetchDiffResponse(), commentResponse, null, false);

        assertEquals(Result.SUCCESS, build.getResult());
    }

    @Test
    public void testBuildWithErrorOnArcanist() throws Exception {
        wrapper.getDescriptor().setArcPath("false");
        JSONObject commentResponse = new JSONObject();
        FreeStyleBuild build = buildWithConduit(getFetchDiffResponse(), commentResponse, null, true);

        assertFailureWithMessage("Error applying arc patch", build);
    }

    @Override
    protected void addBuildStep() {
        p.getBuildWrappersList().add(wrapper);
    }
}
