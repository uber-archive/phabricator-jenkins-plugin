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

import com.uber.jenkins.phabricator.coverage.CodeCoverageMetrics;
import com.uber.jenkins.phabricator.utils.CommonUtils;
import com.uber.jenkins.phabricator.utils.Logger;
import hudson.model.Result;

class CommentBuilder {
    private static final String UBERALLS_TAG = "uberalls";
    private final Logger logger;
    private final CodeCoverageMetrics currentCoverage;
    private final StringBuilder comment;
    private final String buildURL;
    private final Result result;
    private final boolean preserveFormatting;

    public CommentBuilder(Logger logger, Result result, CodeCoverageMetrics currentCoverage, String buildURL, boolean preserveFormatting) {
        this.logger = logger;
        this.result = result;
        this.currentCoverage = currentCoverage;
        this.buildURL = buildURL;
        this.preserveFormatting = preserveFormatting;
        this.comment = new StringBuilder();
    }

    /**
     * Get the final comment to post to Phabricator
     * @return
     */
    public String getComment() {
        return comment.toString();
    }

    /**
     * Determine whether to attempt to process coverage
     * @return
     */
    public boolean hasCoverageAvailable() {
        return currentCoverage != null && currentCoverage.getLineCoveragePercent() > 0.0f;
    }

    /**
     * Query uberalls for parent coverage and add appropriate comment
     * @param parentCoverage the parent coverage returned from uberalls
     * @param baseCommit
     * @param branchName the name of the current branch
     */
    public void processParentCoverage(CodeCoverageMetrics parentCoverage, String baseCommit, String branchName) {
        if (parentCoverage == null) {
            logger.info(UBERALLS_TAG, "unable to find coverage for parent commit");
            return;
        }

        Float lineCoveragePercent = currentCoverage.getLineCoveragePercent();

        logger.info(UBERALLS_TAG, "line coverage: " + lineCoveragePercent);
        logger.info(UBERALLS_TAG, "found parent coverage as " + parentCoverage.getLineCoveragePercent());

        float coverageDelta = lineCoveragePercent - parentCoverage.getLineCoveragePercent();

        String coverageDeltaDisplay = String.format("%.3f", coverageDelta);
        String lineCoverageDisplay = String.format("%.3f", lineCoveragePercent);

        if (coverageDelta > 0) {
            comment.append("Coverage increased (+" + coverageDeltaDisplay + "%) to " + lineCoverageDisplay + "%");
        } else if (coverageDelta < 0) {
            comment.append("Coverage decreased (" + coverageDeltaDisplay + "%) to " + lineCoverageDisplay + "%");
        } else {
            comment.append("Coverage remained the same (" + lineCoverageDisplay + "%)");
        }

        comment.append(" when pulling **" + branchName + "** into ");
        comment.append(baseCommit.substring(0, 7));
        comment.append(".");
    }

    public void processBuildResult(boolean commentOnSuccess, boolean commentWithConsoleLinkOnFailure, boolean runHarbormaster) {
        if (result == Result.SUCCESS) {
            if (comment.length() == 0 && (commentOnSuccess || !runHarbormaster)) {
                comment.append("Build is green");
            }
        } else if (result == Result.UNSTABLE) {
            comment.append("Build is unstable");
        } else if (result == Result.FAILURE) {
            if (!runHarbormaster || commentWithConsoleLinkOnFailure) {
                comment.append("Build has FAILED");
            }
        } else if (result == Result.ABORTED) {
            comment.append("Build was aborted");
        } else {
            logger.info(UBERALLS_TAG, "Unknown build status " + result.toString());
        }
    }

    /**
     * Add user-defined content via a .phabricator-comment file
     * @param customComment the contents of the file
     */
    public void addUserComment(String customComment) {
        if (CommonUtils.isBlank(customComment)) {
            return;
        }

        // Ensure we separate previous parts of the comment with newlines
        if (hasComment()) {
            comment.append("\n\n");
        }
        if (preserveFormatting) {
            comment.append(String.format("%s\n", customComment));
        } else {
            comment.append(String.format("```\n%s\n```\n\n", customComment));
        }
    }

    /**
     * Determine if there exists a comment already
     * @return
     */
    public boolean hasComment() {
        return comment.length() > 0;
    }

    /**
     * Add a build link to the comment
     */
    public void addBuildLink() {
        comment.append(String.format(" %s for more details.", buildURL));
    }

    /**
     * Add a build failure message to the comment
     */
    public void addBuildFailureMessage() {
        comment.append(String.format("\n\nLink to build: %s", buildURL));
        comment.append(String.format("\nSee console output for more information: %sconsole", buildURL));
    }
}
