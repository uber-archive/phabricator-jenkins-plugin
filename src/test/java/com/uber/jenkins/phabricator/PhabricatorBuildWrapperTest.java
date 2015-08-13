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

import com.uber.jenkins.phabricator.conduit.ConduitAPIClientTest;
import com.uber.jenkins.phabricator.utils.TestUtils;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import net.sf.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class PhabricatorBuildWrapperTest extends BuildIntegrationTest {
    private PhabricatorBuildWrapper wrapper;
    private FakeConduit conduit;

    @Before
    public void setUp() throws Exception {
        p = createProject();
        wrapper = new PhabricatorBuildWrapper(
                false,
                false,
                true
        );
        wrapper.getDescriptor().setArcPath("echo");
    }

    @After
    public void tearDown() throws Exception {
        if (conduit != null) {
            conduit.stop();
        }
    }

    @Test
    public void testGetters() {
        assertFalse(wrapper.isCreateCommit());
        assertFalse(wrapper.isApplyToMaster());
        assertTrue(wrapper.isShowBuildStartedMessage());
    }

    @Test
    public void testRoundTripConfiguration() throws Exception {
        p.getBuildWrappersList().add(wrapper);

        j.submit(j.createWebClient().getPage(p, "configure").getFormByName("config"));

        PhabricatorBuildWrapper after = p.getBuildWrappersList().get(PhabricatorBuildWrapper.class);
        j.assertEqualBeans(wrapper, after,
                "createCommit,applyToMaster,showBuildStartedMessage");
    }

    @Test
    public void testNoParameterBuild() throws Exception {
        p.getBuildWrappersList().add(wrapper);

        FreeStyleBuild build = p.scheduleBuild2(0).get();
        Result result = build.getResult();
        assertSuccessfulBuild(result);
    }

    @Test
    public void testBuildNoConduit() throws Exception {
        p.getBuildWrappersList().add(wrapper);
        TestUtils.setDefaultBuildEnvironment(j);

        FreeStyleBuild build = p.scheduleBuild2(0).get();
        assertEquals(Result.FAILURE, build.getResult());
    }

    @Test
    public void testBuildInvalidConduit() throws Exception {
        TestUtils.addInvalidCredentials();
        p.getBuildWrappersList().add(wrapper);
        TestUtils.setDefaultBuildEnvironment(j);

        FreeStyleBuild build = p.scheduleBuild2(0).get();
        assertEquals(Result.FAILURE, build.getResult());
    }

    @Test
    public void testBuildValidConduitEmptyResponse() throws Exception {
        FreeStyleBuild build = buildWithConduit(null, null);
        assertEquals(Result.FAILURE, build.getResult());
    }

    @Test
    public void testBuildValidErrorCommenting() throws Exception {
        FreeStyleBuild build = buildWithConduit(getFetchDiffResponse(), null);
        assertEquals(Result.FAILURE, build.getResult());
    }

    @Test
    public void testBuildValidSuccess() throws Exception {
        JSONObject commentResponse = new JSONObject();
        FreeStyleBuild build = buildWithConduit(getFetchDiffResponse(), commentResponse);

        assertEquals(Result.SUCCESS, build.getResult());
        PhabricatorPostbuildSummaryAction action = build.getAction(PhabricatorPostbuildSummaryAction.class);
        assertNotNull(action);
        assertEquals("sc@ndella.com", action.getAuthorEmail());
        assertEquals("aiden", action.getAuthorName());
        assertNotNull(action.getIconPath());
    }

    @Test
    public void testBuildWithErrorOnArcanist() throws Exception {
        wrapper.getDescriptor().setArcPath("false");
        JSONObject commentResponse = new JSONObject();
        FreeStyleBuild build = buildWithConduit(getFetchDiffResponse(), commentResponse);

        assertEquals(Result.FAILURE, build.getResult());
    }

    private JSONObject getFetchDiffResponse() throws IOException {
        return TestUtils.getJSONFromFile(ConduitAPIClientTest.class, "validFetchDiffResponse");
    }

    private FreeStyleBuild buildWithConduit(JSONObject queryDiffsResponse, JSONObject postCommentResponse) throws Exception {
        Map<String, JSONObject> responses = new HashMap<String, JSONObject>();
        if (queryDiffsResponse != null) {
            responses.put("differential.querydiffs", queryDiffsResponse);
        }
        if (postCommentResponse != null) {
            responses.put("differential.createcomment", postCommentResponse);
        }
        conduit = new FakeConduit(responses);

        TestUtils.addValidCredentials(conduit);

        p.getBuildWrappersList().add(wrapper);
        TestUtils.setDefaultBuildEnvironment(j);

        return p.scheduleBuild2(0).get();
    }

}
