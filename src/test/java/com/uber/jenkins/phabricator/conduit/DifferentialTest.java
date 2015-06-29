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

import hudson.EnvVars;
import junit.framework.TestCase;
import net.sf.json.JSONObject;
import net.sf.json.groovy.JsonSlurper;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

public class DifferentialTest extends TestCase {
    private static final String FAKE_DIFF_ID = "not-a-real-id";

    Differential differential;

    protected void setUp() throws IOException, ArcanistUsageException, InterruptedException {
        differential = new Differential(getValidQueryResponse());
    }

    @Test
    public void testFetchRevisionID() throws Exception {
        assertEquals(FAKE_DIFF_ID, differential.getRevisionID(false));
    }

    @Test
    public void testGetPhabricatorLink() throws Exception {
        assertTrue(differential.getPhabricatorLink("http://example.com").contains(FAKE_DIFF_ID));
    }

    @Test
    public void testGetPhabricatorLinkInvalidURL() throws Exception {
        // Try our best to join URLs, even when they are wrong
        assertTrue(differential.getPhabricatorLink("aoeu").contains("aoeu"));
    }

    @Test
    public void testGetBuildStartedMessage() throws Exception {
        assertTrue(differential.getBuildStartedMessage(new EnvVars()).contains("Build started"));
    }

    private JSONObject getValidQueryResponse() throws IOException {
        InputStream input = getClass().getResourceAsStream(
                "validDifferentialQueryResponse.json"
        );
        return (JSONObject) new JsonSlurper().parse(input);
    }

}
