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

import com.uber.jenkins.phabricator.coverage.CoberturaXMLParserTest;
import com.uber.jenkins.phabricator.utils.TestUtils;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import net.sf.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.TestBuilder;

import java.io.IOException;

import static org.junit.Assert.*;

public class PhabricatorNotifierTest extends BuildIntegrationTest {
    private PhabricatorNotifier notifier;

    @Before
    public void setUp() throws IOException {
        p = createProject();
        notifier = new PhabricatorNotifier(
                false,
                true,
                ".phabricator-comment",
                "1000",
                false
        );
    }

    @Test
    public void testGetters() {
        assertFalse(notifier.isCommentOnSuccess());
        assertTrue(notifier.isUberallsEnabled());
        assertEquals(".phabricator-comment", notifier.getCommentFile());
        assertFalse(notifier.isCommentWithConsoleLinkOnFailure());
    }

    @Test
    public void testRoundTripConfiguration() throws Exception {
        addBuildStep();
        j.submit(j.createWebClient().getPage(p, "configure").getFormByName("config"));

        PhabricatorNotifier after = p.getPublishersList().get(PhabricatorNotifier.class);
        j.assertEqualBeans(notifier, after,
                "commentOnSuccess,uberallsEnabled,commentWithConsoleLinkOnFailure,commentFile");
    }

    @Test
    public void testNoParametersBuild() throws Exception {
        addBuildStep();
        FreeStyleBuild build = p.scheduleBuild2(0).get();
        Result result = build.getResult();

        assertSuccessfulBuild(result);
    }

    @Test
    public void testNoCredentials() throws Exception {
        addBuildStep();
        TestUtils.setDefaultBuildEnvironment(j);

        FreeStyleBuild build = p.scheduleBuild2(0).get();
        assertFailureWithMessage("No credentials configured for conduit", build);
    }

    @Test
    public void testWithCredentialsIgnoresMissingConduit() throws Exception {
        FreeStyleBuild build = buildWithConduit(null, null, null);
        assertEquals(Result.SUCCESS, build.getResult());
        assertLogContains("Unable to fetch differential", build);
    }

    @Test
    public void testUnableToPostToHarbormaster() throws Exception {
        FreeStyleBuild build = buildWithConduit(getFetchDiffResponse(), null, null);

        assertFailureWithMessage("Unable to post to Harbormaster", build);
    }

    @Test
    public void testPostToHarbormaster() throws Exception {
        FreeStyleBuild build = buildWithConduit(getFetchDiffResponse(), null, new JSONObject());

        assertEquals(Result.SUCCESS, build.getResult());
    }

    @Test
    public void testPostCoverage() throws Exception {
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild build, Launcher launcher, BuildListener buildListener) throws InterruptedException, IOException {
                build.getWorkspace().child(TestUtils.COBERTURA_XML).copyFrom(CoberturaXMLParserTest.class.getResourceAsStream("go-torch-coverage.xml"));
                return true;
            }
        });
        p.getPublishersList().add(TestUtils.getDefaultCoberturaPublisher());

        FreeStyleBuild build = buildWithConduit(getFetchDiffResponse(), null, new JSONObject());
        assertEquals(Result.SUCCESS, build.getResult());
        assertLogContains("Publishing coverage data to Harbormaster for 3 files", build);
    }

    @Test
    public void testDescriptor() {
        PhabricatorNotifierDescriptor descriptor = notifier.getDescriptor();

        assertNull(descriptor.getCredentialsID());
        assertNull(descriptor.getUberallsURL());
        assertNull(descriptor.getCommentSize());
        assertNull(descriptor.getCommentFile());

        descriptor.setCommentFile("hello.world");
        descriptor.setCommentSize("1000");
        descriptor.setCredentialsID("not-a-real-uuid");
        descriptor.setUberallsURL("http://uber.alls");
    }

    @Override
    protected void addBuildStep() {
        p.getPublishersList().add(notifier);
    }
}
