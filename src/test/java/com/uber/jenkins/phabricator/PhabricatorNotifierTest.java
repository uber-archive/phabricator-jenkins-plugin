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

import com.uber.jenkins.phabricator.coverage.CoberturaXMLParser;
import com.uber.jenkins.phabricator.lint.LintResult;
import com.uber.jenkins.phabricator.lint.LintResults;
import com.uber.jenkins.phabricator.uberalls.UberallsClient;
import com.uber.jenkins.phabricator.unit.JUnitTestProvider;
import com.uber.jenkins.phabricator.utils.Logger;
import com.uber.jenkins.phabricator.utils.TestUtils;
import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import net.sf.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

public class PhabricatorNotifierTest extends BuildIntegrationTest {
    private PhabricatorNotifier notifier;

    @Before
    public void setUp() throws IOException {
        p = createProject();
        notifier = new PhabricatorNotifier(
                false,
                true,
                false,
                0.0,
                true,
                ".phabricator-comment",
                "1001",
                false,
                true,
                true,
                ".phabricator-lint",
                "10000"
        );
    }

    @Test
    public void testGetters() {
        assertFalse(notifier.isCommentOnSuccess());
        assertTrue(notifier.isUberallsEnabled());
        assertFalse(notifier.isCoverageCheck());
        assertEquals(0.0, notifier.getCoverageThreshold(), 0);
        assertTrue(notifier.isPreserveFormatting());
        assertEquals(".phabricator-comment", notifier.getCommentFile());
        assertEquals("1001", notifier.getCommentSize());
        assertFalse(notifier.isCommentWithConsoleLinkOnFailure());
        assertTrue(notifier.isProcessLint());
        assertEquals(".phabricator-lint", notifier.getLintFile());
        assertEquals("10000", notifier.getLintFileSize());
    }

    @Test
    public void testRoundTripConfiguration() throws Exception {
        addBuildStep();
        j.submit(j.createWebClient().getPage(p, "configure").getFormByName("config"));

        PhabricatorNotifier after = p.getPublishersList().get(PhabricatorNotifier.class);
        j.assertEqualBeans(notifier, after,
                "commentOnSuccess,uberallsEnabled,commentWithConsoleLinkOnFailure,commentFile,commentSize");
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
    public void testPostToHarbormasterValidLint() throws Exception {
        JSONObject json = new JSONObject();
        LintResults result = new LintResults();
        result.add(new LintResult("test", "testcode", "error", "to/path", 10, 3, "test description"));
        json.element("lint", result);
        FreeStyleBuild build = buildWithConduit(getFetchDiffResponse(), null, json);

        assertEquals(Result.SUCCESS, build.getResult());
    }

    @Test
    public void testPostCoverage() throws Exception {
        TestUtils.addCopyBuildStep(p, TestUtils.COBERTURA_XML, CoberturaXMLParser.class, "go-torch-coverage.xml");
        p.getPublishersList().add(TestUtils.getDefaultCoberturaPublisher());

        FreeStyleBuild build = buildWithConduit(getFetchDiffResponse(), null, new JSONObject());
        assertEquals(Result.SUCCESS, build.getResult());
        assertLogContains("Publishing coverage data to Harbormaster for 3 files", build);
    }

    @Test
    public void testFailBuildOnDecreasedCoverage() throws Exception {
        TestUtils.addCopyBuildStep(p, TestUtils.COBERTURA_XML, CoberturaXMLParser.class, "go-torch-coverage2.xml");
        UberallsClient uberalls = TestUtils.getDefaultUberallsClient();
        notifier = getDecreasedLineCoverageNotifier(0.0);

        when(uberalls.getCoverage(any(String.class))).thenReturn("{\n" +
            "  \"sha\": \"deadbeef\",\n" +
            "  \"lineCoverage\": 100,\n" +
            "  \"filesCoverage\": 100,\n" +
            "  \"packageCoverage\": 100,\n" +
            "  \"classesCoverage\": 100,\n" +
            "  \"methodCoverage\": 100,\n" +
            "  \"conditionalCoverage\": 100\n" +
            "}");
        notifier.getDescriptor().setUberallsURL("http://uber.alls");
        notifier.setUberallsClient(uberalls);

        FreeStyleBuild build = buildWithConduit(getFetchDiffResponse(), null, new JSONObject());
        assertEquals(Result.FAILURE, build.getResult());
    }

    @Test
    public void testPassBuildOnDecreasedCoverageGreaterThanMaxPercent() throws Exception {
        TestUtils.addCopyBuildStep(p, TestUtils.COBERTURA_XML, CoberturaXMLParser.class, "go-torch-coverage2.xml");
        UberallsClient uberalls = TestUtils.getDefaultUberallsClient();
        notifier = getDecreasedLineCoverageNotifier(-5.0);

        when(uberalls.getCoverage(any(String.class))).thenReturn("{\n" +
            "  \"sha\": \"deadbeef\",\n" +
            "  \"lineCoverage\": 100,\n" +
            "  \"filesCoverage\": 100,\n" +
            "  \"packageCoverage\": 100,\n" +
            "  \"classesCoverage\": 100,\n" +
            "  \"methodCoverage\": 100,\n" +
            "  \"conditionalCoverage\": 100\n" +
            "}");
        notifier.getDescriptor().setUberallsURL("http://uber.alls");
        notifier.setUberallsClient(uberalls);

        FreeStyleBuild build = buildWithConduit(getFetchDiffResponse(), null, new JSONObject());
        assertEquals(Result.SUCCESS, build.getResult());
    }

    @Test
    public void testPassBuildOnSameCoverage() throws Exception {
        TestUtils.addCopyBuildStep(p, TestUtils.COBERTURA_XML, CoberturaXMLParser.class, "go-torch-coverage2.xml");
        UberallsClient uberalls = TestUtils.getDefaultUberallsClient();
        notifier = getDecreasedLineCoverageNotifier(0.0);
        when(uberalls.getCoverage(any(String.class))).thenReturn("{\n" +
            "  \"sha\": \"deadbeef\",\n" +
            "  \"lineCoverage\": 0.0,\n" +
            "  \"packageCoverage\": 0,\n" +
            "  \"classesCoverage\": 0,\n" +
            "  \"methodCoverage\": 0,\n" +
            "  \"conditionalCoverage\": 0\n" +
            "}");
        notifier.setUberallsClient(uberalls);

        FreeStyleBuild build = buildWithConduit(getFetchDiffResponse(), null, new JSONObject());
        assertEquals(Result.SUCCESS, build.getResult());
    }

    @Test
    public void testPassBuildOnPositiveMaximumCoverageDecrease() throws Exception {
        TestUtils.addCopyBuildStep(p, TestUtils.COBERTURA_XML, CoberturaXMLParser.class, "go-torch-coverage2.xml");
        UberallsClient uberalls = TestUtils.getDefaultUberallsClient();
        notifier = getDecreasedLineCoverageNotifier(0.01);
        when(uberalls.getCoverage(any(String.class))).thenReturn("{\n" +
            "  \"sha\": \"deadbeef\",\n" +
            "  \"lineCoverage\": 95.2391,\n" +
            "  \"filesCoverage\": 0,\n" +
            "  \"packageCoverage\": 0,\n" +
            "  \"classesCoverage\": 0,\n" +
            "  \"methodCoverage\": 0,\n" +
            "  \"conditionalCoverage\": 0\n" +
            "}");
        notifier.setUberallsClient(uberalls);

        FreeStyleBuild build = buildWithConduit(getFetchDiffResponse(), null, new JSONObject());
        assertEquals(Result.SUCCESS, build.getResult());
    }

    @Test
    public void testPostCoverageWithoutPublisher() throws Exception {
        TestUtils.addCopyBuildStep(p, "src/coverage/" + TestUtils.COBERTURA_XML, CoberturaXMLParser.class, "go-torch-coverage.xml");

        FreeStyleBuild build = buildWithConduit(getFetchDiffResponse(), null, new JSONObject());
        assertEquals(Result.SUCCESS, build.getResult());
        assertLogContains("Publishing coverage data to Harbormaster for 3 files", build);
    }

    @Test
    public void testPostUnit() throws Exception {
        TestUtils.addCopyBuildStep(p, TestUtils.JUNIT_XML, JUnitTestProvider.class, "go-torch-junit.xml");
        p.getPublishersList().add(TestUtils.getDefaultXUnitPublisher());

        FreeStyleBuild build = buildWithConduit(getFetchDiffResponse(), null, new JSONObject());
        assertEquals(Result.SUCCESS, build.getResult());
        assertLogContains("Publishing unit results to Harbormaster for 35 tests", build);
    }

    @Test
    public void testPostUnitWithFailure() throws Exception {
        TestUtils.addCopyBuildStep(p, TestUtils.JUNIT_XML, JUnitTestProvider.class, "go-torch-junit-fail.xml");
        p.getPublishersList().add(TestUtils.getDefaultXUnitPublisher());

        FreeStyleBuild build = buildWithConduit(getFetchDiffResponse(), null, new JSONObject());
        assertEquals(Result.UNSTABLE, build.getResult());
        assertLogContains("Publishing unit results to Harbormaster for 8 tests", build);

        FakeConduit conduitTestClient = getConduitClient();
        // There are two requests, first it fetches the diff info, secondly it posts the unit result to harbormaster
        assertEquals(2, conduitTestClient.getRequestBodies().size());
        String actualUnitResultWithFailureRequestBody = conduitTestClient.getRequestBodies().get(1);

        assertConduitRequest(getUnitResultWithFailureRequest(), actualUnitResultWithFailureRequestBody);
    }

    @Test
    public void testPostCoverageUberallsDisabled() throws Exception {
        notifier = new PhabricatorNotifier(
                false,
                false,
                false,
                0.0,
                true,
                ".phabricator-comment",
                "1000",
                false,
                true,
                true,
                ".phabricator-lint",
                "10000"
        );
        testPostCoverage();
    }

    @Test
    public void testNonDifferentialWithPHID() throws Exception {
        FreeStyleBuild build = buildWithCommit(new JSONObject());

        assertEquals(Result.SUCCESS, build.getResult());
        assertLogContains("Sending diffusion result", build);
    }

    @Test
    public void testDescriptor() {
        PhabricatorNotifierDescriptor descriptor = notifier.getDescriptor();

        assertNull(descriptor.getCredentialsID());
        assertNull(descriptor.getUberallsURL());

        descriptor.setCredentialsID("not-a-real-uuid");
        descriptor.setUberallsURL("http://uber.alls");
    }

    @Override
    protected void addBuildStep() {
        p.getPublishersList().add(notifier);
    }

    protected PhabricatorNotifier getDecreasedLineCoverageNotifier(double threshold) {
        return new PhabricatorNotifier(
            false,
            true,
            true,
            threshold,
            true,
            ".phabricator-comment",
            "1001",
            false,
            true,
            true,
            ".phabricator-lint",
            "10000"
        );
    }
}
