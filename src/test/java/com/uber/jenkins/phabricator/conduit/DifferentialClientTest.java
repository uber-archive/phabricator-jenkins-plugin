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

import com.uber.jenkins.phabricator.LauncherFactory;
import com.uber.jenkins.phabricator.utils.TestUtils;
import net.sf.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

public class DifferentialClientTest {

    private DifferentialClient differentialClient;

    @Before
    public void setUp() throws Exception {
        differentialClient = TestUtils.getDefaultDifferentialClient();
    }

    @Test
    public void testPostComment() throws Exception {
        JSONObject sentinel = new JSONObject();
        sentinel.put("hi", "there");

        mockConduitResponse(differentialClient, sentinel);

        JSONObject response = differentialClient.postComment("hello", true, "none");
        assertEquals(sentinel, response);
    }

    @Test
    public void testPostCommentSingleArgument() throws Exception {
        JSONObject sentinel = new JSONObject();
        sentinel.put("something", "here");

        mockConduitResponse(differentialClient, sentinel);

        JSONObject response = differentialClient.postComment("hello");
        assertEquals(response, sentinel);
    }

    @Test(expected = ArcanistUsageException.class)
    public void testFetchDiffWithEmptyResponse() throws Exception {
        JSONObject empty = new JSONObject();
        mockConduitResponse(differentialClient, empty);

        differentialClient.fetchDiff();
    }

    @Test(expected = ArcanistUsageException.class)
    public void testFetchDiffWithNoDiff() throws Exception {
        JSONObject noDiff = new JSONObject();
        noDiff.put("response", null);
        mockConduitResponse(differentialClient, noDiff);

        differentialClient.fetchDiff();
    }

    @Test
    public void testFetchDiffWithOtherDiff() throws Exception {
        JSONObject otherDiff = TestUtils.getJSONFromFile(getClass(), "fetchDiffResponseMissingDiff");
        mockConduitResponse(differentialClient, otherDiff);

        JSONObject response = differentialClient.fetchDiff();
        assertTrue(response.isEmpty());
    }

    @Test
    public void testFetchDiffWithValidResponse() throws Exception {
        JSONObject realResponse = TestUtils.getJSONFromFile(getClass(), "validFetchDiffResponse");
        mockConduitResponse(differentialClient, realResponse);

        JSONObject response = differentialClient.fetchDiff();
        assertEquals("world", response.get("hello"));
    }

    private void mockConduitResponse(DifferentialClient client, JSONObject response) throws InterruptedException, ArcanistUsageException, IOException {
        doReturn(response).when(client).callConduit(
                anyString(),
                anyMap()
        );
    }
}
