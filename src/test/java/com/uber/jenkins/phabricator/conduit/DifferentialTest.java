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

import com.uber.jenkins.phabricator.PhabricatorPostbuildSummaryAction;
import com.uber.jenkins.phabricator.utils.TestUtils;
import hudson.EnvVars;
import junit.framework.TestCase;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import org.junit.Test;

import java.io.IOException;

public class DifferentialTest extends TestCase {
    private static final String FAKE_DIFF_ID = "not-a-real-id";

    Differential differential;

    protected void setUp() throws IOException, ArcanistUsageException, InterruptedException {
        JSONObject response = TestUtils.getJSONFromFile(getClass(), "validDifferentialQueryResponse");
        differential = new Differential(response);
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

    @Test
    public void testGetBranch() {
        assertEquals("a-branch-name", differential.getBranch());
    }

    @Test
    public void testGetBranchWithEmptyResponse() throws Exception {
        JSONObject empty = new JSONObject();
        empty.put("branch", JSONNull.getInstance());
        Differential diff = new Differential(empty);
        assertEquals("(none)", diff.getBranch());
    }

    @Test
    public void testGetBranchWithInvalidResponse() throws Exception {
        JSONObject invalid = new JSONObject();
        invalid.put("branch", true);
        Differential diff = new Differential(invalid);
        assertEquals("(unknown)", diff.getBranch());
    }

    @Test
    public void testGetBaseCommit() throws Exception {
        assertEquals("aaaaaaaa", differential.getBaseCommit());
    }

    @Test
    public void testGetSummaryMessage() throws Exception {
        PhabricatorPostbuildSummaryAction summary = differential.createSummary("http://example.com");
        assertEquals("http://example.com/Dnot-a-real-id", summary.getUrl());
        assertEquals("Dnot-a-real-id", summary.getRevisionID());
        assertEquals("aiden", summary.getAuthorName());
        assertEquals("ai@uber.com", summary.getAuthorEmail());
    }
}
