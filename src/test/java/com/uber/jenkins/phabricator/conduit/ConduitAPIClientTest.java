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
import org.apache.commons.httpclient.HttpStatus;
import org.apache.http.localserver.LocalTestServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ConduitAPIClientTest {
    private LocalTestServer server;
    private ConduitAPIClient client;
    private final JSONObject emptyParams = new JSONObject();

    @Before
    public void setUp() throws Exception {
        server = new LocalTestServer(null, null);
        server.start();
    }

    @After
    public void tearDown() throws Exception {
        server.stop();
    }

    @Test(expected = ConduitAPIException.class)
    public void testInvalidURI() throws Exception {
        client = new ConduitAPIClient("gopher://foo", TestUtils.TEST_CONDUIT_TOKEN);
        client.perform("anything", emptyParams);
    }

    @Test
    public void testSuccessfullFetch() throws Exception {
        server.register("/api/valid", TestUtils.makeHttpHandler(HttpStatus.SC_OK, "{\"hello\": \"world\"}"));

        client = new ConduitAPIClient(getTestServerAddress(), TestUtils.TEST_CONDUIT_TOKEN);
        JSONObject response = client.perform("valid", emptyParams);
        assertEquals("world", response.getString("hello"));
    }

    @Test(expected = ConduitAPIException.class)
    public void testBadRequestErrorCode() throws Exception {
        server.register("/api/foo", TestUtils.makeHttpHandler(HttpStatus.SC_BAD_REQUEST, "nothing"));

        client = new ConduitAPIClient(getTestServerAddress(), TestUtils.TEST_CONDUIT_TOKEN);
        client.perform("foo", emptyParams);
    }

    @Test
    public void testWithParams() throws UnsupportedEncodingException, ConduitAPIException {
        client = new ConduitAPIClient("http://foo.bar", TestUtils.TEST_CONDUIT_TOKEN);
        Map<String, String> params = new HashMap<String, String>();
        params.put("hello", "world");
        client.createRequest("action", params);
    }

    private String getTestServerAddress() {
        return String.format(
                "http://%s:%s",
                server.getServiceAddress().getHostName(),
                server.getServiceAddress().getPort()
        );
    }
}
