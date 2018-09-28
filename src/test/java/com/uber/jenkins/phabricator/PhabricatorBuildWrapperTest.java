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

import com.google.common.collect.Lists;
import com.uber.jenkins.phabricator.utils.TestUtils;

import net.sf.json.JSONObject;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.FreeStyleBuild;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Result;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class PhabricatorBuildWrapperTest extends BuildIntegrationTest {

    private PhabricatorBuildWrapper wrapper;

    @Before
    public void setUp() throws Exception {
        p = createProject();
        wrapper = new PhabricatorBuildWrapper(
                false,
                false,
                false,
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
        assertFalse(wrapper.isPatchWithForceFlag());
        assertFalse(wrapper.isSkipApplyPatch());
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
        assertEquals(Result.FAILURE, build.getResult());
    }

    @Test
    public void testBuildValidConduitEmptyResponse() throws Exception {
        FreeStyleBuild build = buildWithConduit(null, null, null, true);

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

    @Test
    public void getAbortOnRevisionIdIfAvailable() throws Exception {
        FreeStyleBuild build = buildWithConduit(getFetchDiffResponse(), null, null, true);
        assertNull(PhabricatorBuildWrapper.getAbortOnRevisionId(build));

        List<ParameterValue> parameters = Lists.newArrayList();
        parameters.add(new ParameterValue("ABORT_ON_REVISION_ID") {
            @Override
            public Object getValue() {
                return "test";
            }
        });
        build.addAction(new ParametersAction(parameters));
        assertEquals("test", PhabricatorBuildWrapper.getAbortOnRevisionId(build));
    }

    @Test
    public void getUpstreamRunIfAvailable() throws Exception {
        FreeStyleBuild build = buildWithConduit(getFetchDiffResponse(), null, null, true);
        FreeStyleBuild upstream = buildWithConduit(getFetchDiffResponse(), null, null, true);
        assertNull(PhabricatorBuildWrapper.getUpstreamRun(build));

        List<Cause> causes = build.getAction(CauseAction.class).getCauses();
        ArrayList<Cause> newCauses = new ArrayList<Cause>(causes);
        newCauses.add((new Cause.UpstreamCause(upstream)));
        build.replaceAction(new CauseAction(newCauses));

        assertEquals(upstream, PhabricatorBuildWrapper.getUpstreamRun(build));
    }

    @Test
    public void skipApplyPatchDoesNotFailPatching() throws Exception {
        p = createProject();
        wrapper = new PhabricatorBuildWrapper(
                false,
                false,
                false,
                false,
                false,
                true
        );
        wrapper.getDescriptor().setArcPath("false");
        p.getBuildWrappersList().add(wrapper);
        FreeStyleBuild build = p.scheduleBuild2(0).get();
        Result result = build.getResult();
        assertSuccessfulBuild(result);
    }

    @Override
    protected void addBuildStep() {
        p.getBuildWrappersList().add(wrapper);
    }
}
