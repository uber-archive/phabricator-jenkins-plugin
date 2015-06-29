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

package com.uber.jenkins.phabricator.tasks;

import com.uber.jenkins.phabricator.CodeCoverageMetrics;
import com.uber.jenkins.phabricator.uberalls.UberallsClient;
import com.uber.jenkins.phabricator.utils.TestUtils;
import hudson.plugins.cobertura.targets.CoverageResult;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

import static org.mockito.Mockito.*;

public class GenericBuildTaskTest {

    private UberallsClient uberallsClient;
    private CodeCoverageMetrics codeCoverageMetrics;

    @Before
    public void setup() {
        uberallsClient = TestUtils.getDefaultUberallsClient();
        codeCoverageMetrics = TestUtils.getDefaultCodeCoverageMetrics();
    }

    @Test
    public void testNullCoverageResult() {
        assertEquals(Task.Result.IGNORED,
                new GenericBuildTask(TestUtils.getDefaultLogger(), uberallsClient, null, true,
                        TestUtils.TEST_SHA).run());
    }

    @Test
    public void testUberallsDisabled() {
        assertEquals(Task.Result.SKIPPED,
                new GenericBuildTask(TestUtils.getDefaultLogger(), uberallsClient,
                        codeCoverageMetrics, false, TestUtils.TEST_SHA).run());
    }

    @Test
    public void testNullBaseURL() {
        uberallsClient = TestUtils.getUberallsClient(null,
                TestUtils.getDefaultLogger(), TestUtils.TEST_REPOSITORY, TestUtils.TEST_BRANCH);
        assertEquals(Task.Result.SKIPPED,
                new GenericBuildTask(TestUtils.getDefaultLogger(), uberallsClient,
                        codeCoverageMetrics, true, TestUtils.TEST_SHA).run());
    }

    @Test
    public void testEmptyBaseURL() {
        uberallsClient = TestUtils.getUberallsClient("",
                TestUtils.getDefaultLogger(), TestUtils.TEST_REPOSITORY, TestUtils.TEST_BRANCH);
        assertEquals(Task.Result.SKIPPED,
                new GenericBuildTask(TestUtils.getDefaultLogger(), uberallsClient,
                        codeCoverageMetrics, true, TestUtils.TEST_SHA).run());
    }

    @Test
    public void testBlankBaseURL() {
        uberallsClient = TestUtils.getUberallsClient("    ",
                TestUtils.getDefaultLogger(), TestUtils.TEST_REPOSITORY, TestUtils.TEST_BRANCH);
        assertEquals(Task.Result.SKIPPED,
                new GenericBuildTask(TestUtils.getDefaultLogger(), uberallsClient,
                        codeCoverageMetrics, true, TestUtils.TEST_SHA).run());
    }

    @Test
    public void testNullCommitSha() {
        assertEquals(Task.Result.IGNORED,
                new GenericBuildTask(TestUtils.getDefaultLogger(), uberallsClient,
                        codeCoverageMetrics, true, null).run());
    }

    @Test
    public void testEmptyCommitSha() {
        assertEquals(Task.Result.IGNORED,
                new GenericBuildTask(TestUtils.getDefaultLogger(), uberallsClient,
                        codeCoverageMetrics, true, "").run());
    }

    @Test
    public void testBlankCommitSha() {
        assertEquals(Task.Result.IGNORED,
                new GenericBuildTask(TestUtils.getDefaultLogger(), uberallsClient,
                        codeCoverageMetrics, true, "    ").run());
    }

    @Test
    public void testInvalidCodeCoverageMetrics() {
        codeCoverageMetrics = TestUtils.getCodeCoverageMetrics(TestUtils.TEST_SHA, 50.0f, 50.0f,
                50.0f, 50.0f, -1.0f, 50.0f);
        assertEquals(Task.Result.IGNORED,
                new GenericBuildTask(TestUtils.getDefaultLogger(), uberallsClient,
                        codeCoverageMetrics, true, TestUtils.TEST_SHA).run());
    }

    @Test
    public void testFailure() {
        when(uberallsClient.recordCoverage(eq(TestUtils.TEST_SHA), eq(codeCoverageMetrics)))
                .thenReturn(false);
        assertEquals(Task.Result.FAILURE,
                new GenericBuildTask(TestUtils.getDefaultLogger(), uberallsClient,
                        codeCoverageMetrics, true, TestUtils.TEST_SHA).run());
    }

    @Test
    public void testSuccess() {
        when(uberallsClient.recordCoverage(eq(TestUtils.TEST_SHA), eq(codeCoverageMetrics)))
                .thenReturn(true);
        assertEquals(Task.Result.SUCCESS,
                new GenericBuildTask(TestUtils.getDefaultLogger(), uberallsClient,
                        codeCoverageMetrics, true, TestUtils.TEST_SHA).run());
    }
}
