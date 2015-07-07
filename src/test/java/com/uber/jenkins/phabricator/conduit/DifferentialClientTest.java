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

package com.uber.jenkins.phabricator.conduit;

import com.uber.jenkins.phabricator.utils.TestUtils;
import net.sf.json.JSONObject;
import net.sf.json.groovy.JsonSlurper;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.io.InputStream;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class DifferentialClientTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private final String DUMMY_DIFF_ID = "123";
    private final String DUMMY_CONDUIT_TOKEN = "notarealtoken";
    private final String DUMMY_ARC_PATH = "echo";

    private DifferentialClient client;

    @Before
    public void setUp() throws Exception {
        client = createClient();
    }

    @Test
    public void testPostComment() throws Exception {
        JSONObject sentinel = new JSONObject();
        sentinel.put("hi", "there");

        mockConduitResponse(client, sentinel);

        JSONObject response = client.postComment("hello", true, "none");
        assertEquals(sentinel, response);
    }

    @Test
    public void testPostCommentSingleArgument() throws Exception {
        JSONObject sentinel = new JSONObject();
        sentinel.put("something", "here");

        mockConduitResponse(client, sentinel);
        JSONObject response = client.postComment("hello");
        assertEquals(response, sentinel);
    }

    @Test(expected = ArcanistUsageException.class)
    public void testFetchDiffWithEmptyResponse() throws Exception {
        JSONObject empty = new JSONObject();
        mockConduitResponse(client, empty);

        client.fetchDiff();
    }

    @Test(expected = ArcanistUsageException.class)
    public void testFetchDiffWithNoDiff() throws Exception {
        JSONObject noDiff = new JSONObject();
        noDiff.put("response", null);
        mockConduitResponse(client, noDiff);

        client.fetchDiff();
    }

    @Test
    public void testFetchDiffWithValidResponse() throws Exception {
        JSONObject realResponse = getValidFetchDiffResponse();
        mockConduitResponse(client, realResponse);

        JSONObject response = client.fetchDiff();
        assertEquals("world", response.get("hello"));
    }


    private void mockConduitResponse(DifferentialClient client, JSONObject response) throws InterruptedException, ArcanistUsageException, IOException {
        doReturn(response).when(client).callConduit(
                anyString(),
                anyMap()
        );
    }

    private DifferentialClient createClient() throws Exception {
        return spy(new DifferentialClient(
                DUMMY_DIFF_ID,
                TestUtils.createLauncherFactory(j),
                DUMMY_CONDUIT_TOKEN,
                DUMMY_ARC_PATH
        ));
    }


    private JSONObject getValidFetchDiffResponse() throws IOException {
        InputStream input = getClass().getResourceAsStream(
                "validFetchDiffResponse.json"
        );
        return (JSONObject) new JsonSlurper().parse(input);
    }
}
