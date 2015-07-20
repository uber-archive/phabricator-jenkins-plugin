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

import com.uber.jenkins.phabricator.utils.Logger;
import com.uber.jenkins.phabricator.utils.TestUtils;
import hudson.model.Result;
import hudson.plugins.cobertura.targets.CoverageResult;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class CommentBuilderTest {
    private static final Logger logger = new Logger(System.out);
    private static final String FAKE_BUILD_URL = "http://example.com/job/123";
    private static final String FAKE_BRANCH_NAME = "oober-is-great";

    private CommentBuilder commenter;

    @Before
    public void setUp() {
        commenter = createCommenter(Result.SUCCESS, TestUtils.getDefaultCoverageResult());
    }

    @Test
    public void testHasCoverageAvailable() {
        assertTrue(commenter.hasCoverageAvailable());
    }

    @Test
    public void testHasNoCoverageAvailable() {
        CommentBuilder commenter = createCommenter(Result.SUCCESS, null);
        assertFalse(commenter.hasCoverageAvailable());
    }

    @Test
    public void testHasNoLineCoverage() {
        CommentBuilder commenter = createCommenter(Result.SUCCESS, TestUtils.getEmptyCoverageResult());
        assertFalse(commenter.hasCoverageAvailable());
    }

    @Test
    public void testProcessParentWithNullResult() {
        commenter.processParentCoverage(null, FAKE_BRANCH_NAME);
        assertFalse(commenter.hasComment());
    }

    @Test
    public void testProcessParentWithMatchingCoverage() {
        commenter.processParentCoverage(TestUtils.getDefaultCodeCoverageMetrics(), FAKE_BRANCH_NAME);
        String comment = commenter.getComment();

        assertTrue(comment.contains("remained the same"));
    }

    @Test
    public void testProcessParentWithIncreasedCoverage() {
        CodeCoverageMetrics parent = TestUtils.getCodeCoverageMetrics(
                TestUtils.TEST_SHA,
                100.0f,
                100.0f,
                100.0f,
                100.0f,
                90.0f,
                90.0f
        );
        commenter.processParentCoverage(parent, FAKE_BRANCH_NAME);
        String comment = commenter.getComment();

        assertTrue(comment.contains("increased (+10.000%)"));
        assertTrue(
                "comment contains branch name",
                comment.contains(FAKE_BRANCH_NAME)
        );
    }

    @Test
    public void testProcessWithDecrease() {
        CoverageResult fiftyPercentDrop = TestUtils.getCoverageResult(1.0f, 1.0f, 1.0f, 1.0f, 0.5f);
        CommentBuilder commenter = createCommenter(Result.SUCCESS, fiftyPercentDrop);
        commenter.processParentCoverage(TestUtils.getDefaultCodeCoverageMetrics(), FAKE_BRANCH_NAME);
        String comment = commenter.getComment();

        assertTrue(comment.contains("decreased (-50.000%)"));
    }

    @Test
    public void testProcessBuildResultSuccess() {
        commenter.processBuildResult(false, false, true);
        assertTrue(
                "no message expected for successful builds unless asked for",
                commenter.getComment().length() == 0
        );
    }

    @Test
    public void testProcessBuildResultSuccessWithComment() {
        commenter.processBuildResult(true, false, false);
        assertEquals("Build is green", commenter.getComment());
    }

    @Test
    public void testProcessBuildResultUnstable() {
        CommentBuilder commenter = createCommenter(Result.UNSTABLE, null);
        commenter.processBuildResult(true, false, false);
        assertEquals("Build is unstable", commenter.getComment());
    }

    @Test
    public void testProcessBuildResultUnknownStatus() {
        CommentBuilder commenter = createCommenter(Result.NOT_BUILT, null);
        commenter.processBuildResult(true, false, false);
        assertFalse(commenter.hasComment());
    }

    @Test
    public void testProcessBuildResultWithFailureMessage() {
        CommentBuilder commenter = createCommenter(Result.FAILURE, null);
        commenter.processBuildResult(false, true, false);
        assertEquals("Build has FAILED", commenter.getComment());
    }

    @Test
    public void testProcessBuildResultWithoutFailureMessage() {
        CommentBuilder commenter = createCommenter(Result.FAILURE, null);
        commenter.processBuildResult(false, false, true);
        assertEquals(0, commenter.getComment().length());
    }

    @Test
    public void testProcessBuildResultAborted() {
        CommentBuilder commenter = createCommenter(Result.ABORTED, null);
        commenter.processBuildResult(false, false, false);
        assertEquals("Build was aborted", commenter.getComment());
    }

    @Test
    public void testAddUserComment() {
        commenter.addUserComment("hello, world");
        assertEquals("```\nhello, world\n```\n\n", commenter.getComment());
    }

    @Test
    public void testAddUserCommentWithStatus() {
        commenter.processBuildResult(false, false, false);
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

    private CommentBuilder createCommenter(Result result, CoverageResult coverage) {
        return new CommentBuilder(logger, result, coverage, FAKE_BUILD_URL);
    }
}
