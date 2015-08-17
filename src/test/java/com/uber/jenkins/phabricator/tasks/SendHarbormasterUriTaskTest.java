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

package com.uber.jenkins.phabricator.tasks;

import com.uber.jenkins.phabricator.conduit.ConduitAPIException;
import com.uber.jenkins.phabricator.conduit.DifferentialClient;
import com.uber.jenkins.phabricator.utils.TestUtils;
import net.sf.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SendHarbormasterUriTaskTest {
    private final String buildUrl = "http://jenkins.example.com/foo/123";
    private final JSONObject validResponse = new JSONObject();
    private DifferentialClient diffClient;

    @Before
    public void setUp() {
        diffClient = mock(DifferentialClient.class);
    }

    @Test
    public void testUrlHappyPath() throws IOException, ConduitAPIException {
        when(diffClient.sendHarbormasterUri(TestUtils.TEST_PHID, buildUrl)).thenReturn(validResponse);

        assertEquals(Task.Result.SUCCESS, getResult());
    }

    @Test
    public void testErrorInfoResponse() throws IOException, ConduitAPIException {
        JSONObject errorResponse = new JSONObject();
        errorResponse.put("error_info", "i'm having a bad day");
        when(diffClient.sendHarbormasterUri(TestUtils.TEST_PHID, buildUrl)).thenReturn(errorResponse);

        assertEquals(Task.Result.FAILURE, getResult());
    }

    @Test
    public void testConduitAPIFailure() throws IOException, ConduitAPIException {
        when(diffClient.sendHarbormasterUri(TestUtils.TEST_PHID, buildUrl)).thenThrow(ConduitAPIException.class);

        assertEquals(Task.Result.FAILURE, getResult());
    }

    @Test
    public void testIOExceptionFailure() throws IOException, ConduitAPIException {
        when(diffClient.sendHarbormasterUri(TestUtils.TEST_PHID, buildUrl)).thenThrow(IOException.class);

        assertEquals(Task.Result.FAILURE, getResult());
    }

    private Task.Result getResult() {
        return new SendHarbormasterUriTask(
                TestUtils.getDefaultLogger(),
                diffClient,
                TestUtils.TEST_PHID,
                buildUrl
        ).run();
    }
}
