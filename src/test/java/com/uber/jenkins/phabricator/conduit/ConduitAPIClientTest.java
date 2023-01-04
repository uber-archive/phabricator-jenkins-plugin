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

import org.apache.http.HttpStatus;
import org.apache.http.localserver.LocalServerTestBase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.UnsupportedEncodingException;

import static org.junit.Assert.assertEquals;

public class ConduitAPIClientTest extends LocalServerTestBase {

    private final JSONObject emptyParams = new JSONObject();
    private ConduitAPIClient client;

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        this.shutDown();
    }

    @Test(expected = ConduitAPIException.class)
    public void testInvalidURI() throws Exception {
        client = new ConduitAPIClient("gopher://foo", TestUtils.TEST_CONDUIT_TOKEN);
        client.perform("anything", emptyParams);
    }

    @Test
    public void testSuccessfullFetch() throws Exception {
        this.serverBootstrap.registerHandler("/api/valid", TestUtils.makeHttpHandler(HttpStatus.SC_OK, "{\"hello\": \"world\"}"));
        this.start();

        client = new ConduitAPIClient(getTestServerAddress(), TestUtils.TEST_CONDUIT_TOKEN);
        JSONObject response = client.perform("valid", emptyParams);
        assertEquals("world", response.getString("hello"));
    }

    @Test(expected = ConduitAPIException.class)
    public void testBadRequestErrorCode() throws Exception {
        this.serverBootstrap.registerHandler("/api/foo", TestUtils.makeHttpHandler(HttpStatus.SC_BAD_REQUEST, "nothing"));
        this.start();

        client = new ConduitAPIClient(getTestServerAddress(), TestUtils.TEST_CONDUIT_TOKEN);
        client.perform("foo", emptyParams);
    }

    @Test
    public void testWithParams() throws UnsupportedEncodingException, ConduitAPIException {
        client = new ConduitAPIClient("http://foo.bar", TestUtils.TEST_CONDUIT_TOKEN);
        JSONObject params = new JSONObject().element("hello", "world");
        params.put("hello", "world");
        client.createRequest("action", params);
    }

    @Test
    public void testWithUTF8() throws Exception {
        this.serverBootstrap.registerHandler("/api/utf8", TestUtils.makeHttpHandler(HttpStatus.SC_OK, "{}"));
        this.start();

        client = new ConduitAPIClient(getTestServerAddress(), TestUtils.TEST_CONDUIT_TOKEN);
        JSONObject utf8Params = new JSONObject().element("message", "こんにちは世界");
        client.perform("utf8", utf8Params);
    }

    private String getTestServerAddress() {
        return TestUtils.getTestServerAddress(this.server);
    }
}
