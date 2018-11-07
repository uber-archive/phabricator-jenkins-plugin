// Copyright (c) 2018 Uber Technologies, Inc.
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

import com.uber.jenkins.phabricator.coverage.CodeCoverageMetrics;
import com.uber.jenkins.phabricator.utils.Logger;
import com.uber.jenkins.phabricator.utils.TestUtils;

import org.junit.Before;
import org.junit.Test;

import hudson.model.Result;

import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

public class CommentBuilderTest {

    private static final Logger logger = TestUtils.getDefaultLogger();
    private static final String FAKE_BUILD_URL = "http://example.com/job/123";
    private static final String FAKE_BRANCH_NAME = "oober-is-great";

    private CommentBuilder commenter;

    @Before
    public void setUp() {
        commenter = createCommenter(TestUtils.getDefaultCodeCoverageMetrics());
    }

    @Test
    public void testHasCoverageAvailable() {
        assertTrue(commenter.hasCoverageAvailable());
    }

    @Test
    public void testHasNoCoverageAvailable() {
        CommentBuilder commenter = createCommenter(null);
        assertFalse(commenter.hasCoverageAvailable());
    }

    @Test
    public void testHasNoLineCoverage() {
        CommentBuilder commenter = createCommenter(TestUtils.getEmptyCoverageMetrics());
        assertFalse(commenter.hasCoverageAvailable());
    }

    @Test
    public void testProcessParentWithNullResult() {
        commenter.processParentCoverage(null, TestUtils.TEST_SHA, FAKE_BRANCH_NAME);
        assertFalse(commenter.hasComment());
    }

    @Test
    public void testProcessParentWithMatchingCoverage() {
        commenter.processParentCoverage(TestUtils.getDefaultCodeCoverageMetrics(), TestUtils.TEST_SHA,
                FAKE_BRANCH_NAME);
        String comment = commenter.getComment();

        assertTrue(comment.contains("remained the same"));
    }

    @Test
    public void testProcessParentWithIncreasedCoverage() {
        CodeCoverageMetrics parent = TestUtils.getCodeCoverageMetrics(
                100.0f,
                100.0f,
                100.0f,
                100.0f,
                90.0f,
                90.0f,
                90,
                100
        );
        commenter.processParentCoverage(parent, TestUtils.TEST_SHA, FAKE_BRANCH_NAME);
        String comment = commenter.getComment();

        assertTrue(comment.contains("increased (+10.000%)"));
        assertTrue(
                "comment contains branch name",
                comment.contains(FAKE_BRANCH_NAME)
        );
    }

    @Test
    public void testProcessWithDecreaseFailingTheBuild() {
        CodeCoverageMetrics fiftyPercentDrop = TestUtils.getCoverageResult(100.0f, 100.0f, 100.0f, 100.0f, 50.0f, 50, 100);
        CommentBuilder commenter = createCommenter(fiftyPercentDrop, false, -10.0f);
        boolean passCoverage = commenter.processParentCoverage(TestUtils.getDefaultCodeCoverageMetrics(),
                TestUtils.TEST_SHA, FAKE_BRANCH_NAME);
        String comment = commenter.getComment();

        assertFalse(passCoverage);
        assertThat(comment, containsString("decreased (-50.000%)"));
        assertThat(comment, containsString(
                "Build failed because coverage is lower than minimum 100.0% and decreased more than allowed 10.0%."));
    }

    @Test
    public void testProcessWithDecreaseNotFailingTheBuild() {
        CodeCoverageMetrics fivePercentDrop = TestUtils.getCoverageResult(100.0f, 100.0f, 100.0f, 100.0f, 95.0f, 95, 100);
        CommentBuilder commenter = createCommenter(fivePercentDrop, false, -10.0f);
        boolean passCoverage = commenter.processParentCoverage(TestUtils.getDefaultCodeCoverageMetrics(),
                TestUtils.TEST_SHA, FAKE_BRANCH_NAME);
        String comment = commenter.getComment();

        assertTrue(passCoverage);
        assertThat(comment, containsString("decreased (-5.000%)"));
        assertFalse(comment.contains(
                "Build failed because coverage is lower than minimum 100.0% and decreased more than allowed 10.0%."));
    }

    @Test
    public void testProcessWithDecreaseButHigherThanMinNotFailingTheBuild() {
        CodeCoverageMetrics fifteenPercentDrop = TestUtils.getCoverageResult(100.0f, 100.0f, 100.0f, 100.0f, 85.0f, 85, 100);
        CommentBuilder commenter = createCommenter(fifteenPercentDrop, false, -10.0f, 80.0f);
        boolean passCoverage = commenter.processParentCoverage(TestUtils.getDefaultCodeCoverageMetrics(),
                TestUtils.TEST_SHA, FAKE_BRANCH_NAME);
        String comment = commenter.getComment();

        assertTrue(passCoverage);
        assertThat(comment, containsString("decreased (-15.000%)"));
        assertFalse(comment.contains(
                "Build failed because coverage is lower than minimum 80.0% and decreased more than allowed 10.0%."));
    }

    @Test
    public void testProcessWithoutCoverageCheckSettings() {
        CommentBuilder commenter = new CommentBuilder(
                logger,
                TestUtils.getCoverageResult(100.0f, 100.0f, 100.0f, 100.0f, 50.0f, 50, 100), // 50% drop
                FAKE_BUILD_URL,
                false,
                null // coverageCheckSettings
        );

        boolean passCoverage = commenter.processParentCoverage(TestUtils.getDefaultCodeCoverageMetrics(),
                TestUtils.TEST_SHA, FAKE_BRANCH_NAME);
        String comment = commenter.getComment();

        // Should not fail if we don't have coverageCheckSettings.
        assertTrue(passCoverage);
        assertThat(comment, containsString("decreased (-50.000%)"));
        assertFalse(comment.contains("Build failed because coverage is lower"));
    }

    @Test
    public void testProcessBuildResultSuccess() {
        commenter.processBuildResult(Result.SUCCESS, false, false, true);
        assertTrue(
                "no message expected for successful builds unless asked for",
                commenter.getComment().length() == 0
        );
    }

    @Test
    public void testProcessBuildResultSuccessWithComment() {
        commenter.processBuildResult(Result.SUCCESS, true, false, false);
        assertEquals("Build is green", commenter.getComment());
    }

    @Test
    public void testProcessBuildResultUnstable() {
        CommentBuilder commenter = createCommenter(null);
        commenter.processBuildResult(Result.UNSTABLE, true, false, false);
        assertEquals("Build is unstable", commenter.getComment());
    }

    @Test
    public void testProcessBuildResultUnknownStatus() {
        CommentBuilder commenter = createCommenter(null);
        commenter.processBuildResult(Result.NOT_BUILT, true, false, false);
        assertFalse(commenter.hasComment());
    }

    @Test
    public void testProcessBuildResultWithFailureMessage() {
        CommentBuilder commenter = createCommenter(null);
        commenter.processBuildResult(Result.FAILURE, false, true, false);
        assertEquals("Build has FAILED", commenter.getComment());
    }

    @Test
    public void testProcessBuildResultWithoutFailureMessage() {
        CommentBuilder commenter = createCommenter(null);
        commenter.processBuildResult(Result.FAILURE, false, false, true);
        assertEquals(0, commenter.getComment().length());
    }

    @Test
    public void testProcessBuildResultAborted() {
        CommentBuilder commenter = createCommenter(null);
        commenter.processBuildResult(Result.ABORTED, false, false, false);
        assertEquals("Build was aborted", commenter.getComment());
    }

    @Test
    public void testAddUserComment() {
        commenter.addUserComment("hello, world");
        assertEquals("```\nhello, world\n```\n\n", commenter.getComment());
    }

    @Test
    public void testAddUserCommentWithPreservingFormatting() {
        commenter = createCommenter(TestUtils.getDefaultCodeCoverageMetrics(), true);
        commenter.addUserComment("hello, world");
        assertEquals("hello, world\n", commenter.getComment());
    }

    @Test
    public void testAddUserCommentWithStatus() {
        commenter.processBuildResult(Result.SUCCESS, false, false, false);
        commenter.addUserComment("hello, world");
        assertEquals("Build is green\n\n```\nhello, world\n```\n\n", commenter.getComment());
    }

    @Test
    public void testAddEmptyComment() {
        commenter.addUserComment("");
        assertFalse(commenter.hasComment());
    }

    @Test
    public void testAddBuildLink() {
        commenter.addBuildLink();
        String comment = commenter.getComment();
        assertTrue(comment.contains("for more details"));
        assertTrue(comment.contains(FAKE_BUILD_URL));
    }

    @Test
    public void testAddBuildFailureMessage() {
        commenter.addBuildFailureMessage();
        String comment = commenter.getComment();
        assertTrue(comment.contains("See console output"));
        assertTrue(comment.contains("Link to build"));
    }

    private CommentBuilder createCommenter(CodeCoverageMetrics coverage) {
        return createCommenter(coverage, false);
    }

    private CommentBuilder createCommenter(CodeCoverageMetrics coverage, boolean preserveFormatting) {
        return createCommenter(coverage, preserveFormatting, 0.0);
    }

    private CommentBuilder createCommenter(
            CodeCoverageMetrics coverage, boolean preserveFormatting,
            double maxCoverageDecreaseInPercent) {
        return createCommenter(coverage, preserveFormatting, maxCoverageDecreaseInPercent, 100.0);
    }

    private CommentBuilder createCommenter(
            CodeCoverageMetrics coverage, boolean preserveFormatting,
            double maxCoverageDecreaseInPercent, double minCoverageInPercent) {
        return new CommentBuilder(logger, coverage, FAKE_BUILD_URL, preserveFormatting,
                new CoverageCheckSettings(true, maxCoverageDecreaseInPercent, minCoverageInPercent));
    }
}
