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

import com.uber.jenkins.phabricator.conduit.ConduitAPIClient;
import com.uber.jenkins.phabricator.conduit.ConduitAPIClientTest;
import com.uber.jenkins.phabricator.conduit.ConduitAPIException;
import com.uber.jenkins.phabricator.utils.TestUtils;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import net.sf.json.JSONObject;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Rule;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public abstract class BuildIntegrationTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    protected FakeConduit conduit;

    FreeStyleProject p;

    @After
    public void tearDown() throws Exception {
        if (conduit != null) {
            conduit.stop();
        }
    }

    public FakeConduit getConduitClient() {
        return conduit;
    }

    void assertSuccessfulBuild(Result result) {
        assertTrue(result.isCompleteBuild());
        assertTrue(result.isBetterOrEqualTo(Result.SUCCESS));
    }

    FreeStyleProject createProject() throws IOException {
        return j.createFreeStyleProject();
    }

    protected abstract void addBuildStep();

    protected JSONObject getFetchDiffResponse() throws IOException {
        return TestUtils.getJSONFromFile(ConduitAPIClientTest.class, "validFetchDiffResponse");
    }

    protected JSONObject getUnitResultWithFailureRequest() throws IOException {
        return TestUtils.getJSONFromFile(ConduitAPIClientTest.class, "unitResultWithFailureRequest");
    }

    protected FreeStyleBuild buildWithConduit(JSONObject queryDiffsResponse, JSONObject postCommentResponse, JSONObject sendMessageResponse) throws Exception {
        return buildWithConduit(queryDiffsResponse, postCommentResponse, sendMessageResponse, true);
    }

    protected FreeStyleBuild buildWithConduit(JSONObject queryDiffsResponse, JSONObject postCommentResponse, JSONObject sendMessageResponse, boolean harbormaster) throws Exception {
        Map<String, JSONObject> responses = new HashMap<String, JSONObject>();
        if (queryDiffsResponse != null) {
            responses.put("differential.querydiffs", queryDiffsResponse);
        }
        if (postCommentResponse != null) {
            responses.put("differential.createcomment", postCommentResponse);
        }
        if (sendMessageResponse != null) {
            responses.put("harbormaster.sendmessage", sendMessageResponse);
        }
        responses.put("differential.getcommitmessage", new JSONObject().element("result", "commit message"));
        conduit = new FakeConduit(responses);

        TestUtils.addValidCredentials(conduit);

        addBuildStep();

        TestUtils.setDefaultBuildEnvironment(j, harbormaster);

        return p.scheduleBuild2(0).get();
    }

    protected FreeStyleBuild buildWithCommit(JSONObject sendMessageResponse) throws Exception {
        Map<String, JSONObject> responses = new HashMap<String, JSONObject>();
        responses.put("harbormaster.sendmessage", sendMessageResponse);
        responses.put("harbormaster.createartifact", new JSONObject());
        conduit = new FakeConduit(responses);

        TestUtils.addValidCredentials(conduit);

        addBuildStep();

        TestUtils.setDefaultBuildEnvironmentForCommits(j);

        return p.scheduleBuild2(0).get();
    }

    protected void assertLogContains(String needle, AbstractBuild build) throws IOException {
        StringBuilder log = new StringBuilder();
        List<String> logLines = build.getLog(1000);
        for (String s : logLines) {
            log.append(s);
        }
        assertThat(log.toString(), containsString(needle));
    }

    protected void assertConduitRequest(JSONObject expectedRequestParams, String actualRequestBody) throws IOException, ConduitAPIException {
        String conduitTestServerAddress = TestUtils.getTestServerAddress(conduit.getServer());
        ConduitAPIClient conduitTestClient = new ConduitAPIClient(conduitTestServerAddress, TestUtils.TEST_CONDUIT_TOKEN);
        HttpEntityEnclosingRequest request = (HttpEntityEnclosingRequest) conduitTestClient.createRequest("", expectedRequestParams);
        String expectedRequestBody = EntityUtils.toString(request.getEntity());

        assertEquals(expectedRequestBody, actualRequestBody);
    }

    protected void assertFailureWithMessage(String message, AbstractBuild build) throws IOException {
        assertEquals(Result.FAILURE, build.getResult());
        assertLogContains(message, build);
    }
}
