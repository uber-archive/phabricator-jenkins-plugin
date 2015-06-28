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

import hudson.Launcher;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ArcanistClientTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private final Map<String, String> emptyParams = new HashMap<String, String>();

    @Test
    public void testEcho() throws Exception {
        ArcanistClient client = new ArcanistClient("echo", "hello", emptyParams, null);

        int result = client.callConduit(getLauncher().launch(), System.err);
        assertEquals(result, 0);
    }

    @Test
    public void testEchoWithToken() throws Exception {
        ArcanistClient client = new ArcanistClient("echo", "tokentest", emptyParams, "notarealtoken");

        int result = client.callConduit(getLauncher().launch(), System.err);
        assertEquals(result, 0);
    }

    @Test
    public void testParseConduit() throws Exception {
        String jsonString = "{\"hello\": \"world\"}";
        ArcanistClient client = new ArcanistClient("echo", jsonString, emptyParams, null);

        JSONObject result = client.parseConduit(getLauncher().launch(), System.err);
        assertTrue(result.has("hello"));
        assertEquals(result.getString("hello"), "world");
    }

    @Test(expected = ArcanistUsageException.class)
    public void testNonZeroExitCode() throws Exception {
        ArcanistClient client = new ArcanistClient("false", "", emptyParams, null);

        client.parseConduit(getLauncher().launch(), System.err);
    }

    @Test(expected = JSONException.class)
    public void testNonJsonOutput() throws Exception {
        ArcanistClient client = new ArcanistClient("echo", "not-json", emptyParams, null);
        client.parseConduit(getLauncher().launch(), System.err);
    }

    private Launcher getLauncher() {
        return j.createLocalLauncher();
    }
}