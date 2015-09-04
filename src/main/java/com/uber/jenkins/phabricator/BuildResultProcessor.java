// Copyright (c) 2015 Uber
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

package com.uber.jenkins.phabricator;

import com.uber.jenkins.phabricator.conduit.Differential;
import com.uber.jenkins.phabricator.conduit.DifferentialClient;
import com.uber.jenkins.phabricator.coverage.CodeCoverageMetrics;
import com.uber.jenkins.phabricator.coverage.CoverageConverter;
import com.uber.jenkins.phabricator.coverage.CoverageProvider;
import com.uber.jenkins.phabricator.tasks.PostCommentTask;
import com.uber.jenkins.phabricator.tasks.SendHarbormasterResultTask;
import com.uber.jenkins.phabricator.tasks.SendHarbormasterUriTask;
import com.uber.jenkins.phabricator.tasks.Task;
import com.uber.jenkins.phabricator.uberalls.UberallsClient;
import com.uber.jenkins.phabricator.utils.CommonUtils;
import com.uber.jenkins.phabricator.utils.Logger;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Result;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class BuildResultProcessor {
    private static final String LOGGING_TAG = "process-build-result";

    private final Logger logger;
    private final Differential diff;
    private final DifferentialClient diffClient;
    private final String phid;
    private final String buildUrl;
    private final boolean runHarbormaster;
    private final Result buildResult;
    private final FilePath workspace;
    private String commentAction;
    private final CommentBuilder commenter;
    private Map<String, String> harbormasterCoverage;

    public BuildResultProcessor(Logger logger, AbstractBuild build, Differential diff, DifferentialClient diffClient,
                                String phid, CodeCoverageMetrics coverageResult, String buildUrl, boolean preserveFormatting) {
        this.logger = logger;
        this.diff = diff;
        this.diffClient = diffClient;
        this.phid = phid;
        this.buildUrl = buildUrl;

        this.buildResult = build.getResult();
        this.workspace = build.getWorkspace();

        this.commentAction = "none";
        this.commenter = new CommentBuilder(logger, build.getResult(), coverageResult, buildUrl, preserveFormatting);
        this.runHarbormaster = !CommonUtils.isBlank(phid);
    }

    /**
     * Fetch parent coverage data from Uberalls, if available
     * @param uberalls the client to the Uberalls instance
     */
    public void processParentCoverage(UberallsClient uberalls) {
        // First add in info about the change in coverage, if applicable
        if (commenter.hasCoverageAvailable()) {
            if (uberalls.isConfigured()) {
                commenter.processParentCoverage(uberalls.getParentCoverage(diff.getBaseCommit()), diff.getBaseCommit(), diff.getBranch());
            } else {
                logger.info(LOGGING_TAG, "No Uberalls backend configured, skipping...");
            }
        } else {
            logger.info(LOGGING_TAG, "No line coverage found, skipping...");
        }

    }

    /**
     * Add build result data into the commenter
     * @param commentOnSuccess whether a "success" should trigger a comment
     * @param commentWithConsoleLinkOnFailure whether a failure should trigger a console link
     */
    public void processBuildResult(boolean commentOnSuccess, boolean commentWithConsoleLinkOnFailure) {
        commenter.processBuildResult(commentOnSuccess, commentWithConsoleLinkOnFailure, runHarbormaster);
    }

    /**
     * Fetch a remote comment from the build workspace
     * @param commentFile the path pattern of the file
     * @param commentSize the maximum number of bytes to read from the remote file
     */
    public void processRemoteComment(String commentFile, String commentSize) {
        RemoteCommentFetcher commentFetcher = new RemoteCommentFetcher(workspace, logger, commentFile, commentSize);
        try {
            String customComment = commentFetcher.getRemoteComment();
            commenter.addUserComment(customComment);
        } catch (InterruptedException e) {
            e.printStackTrace(logger.getStream());
        } catch (IOException e) {
            e.printStackTrace(logger.getStream());
        }
    }

    /**
     * Send a comment to the differential, if present
     * @param commentWithConsoleLinkOnFailure whether we should provide a console link on failure
     */
    public void sendComment(boolean commentWithConsoleLinkOnFailure) {
        if (!commenter.hasComment()) {
            return;
        }

        if (commentWithConsoleLinkOnFailure && buildResult.isWorseOrEqualTo(hudson.model.Result.UNSTABLE)) {
            commenter.addBuildFailureMessage();
        } else {
            commenter.addBuildLink();
        }

        new PostCommentTask(logger, diffClient, diff.getRevisionID(false), commenter.getComment(), commentAction).run();
    }

    /**
     * Send Harbormaster result to Phabricator
     * @return whether we were able to successfully send the result
     */
    public boolean processHarbormaster() {
        final boolean harbormasterSuccess = buildResult.isBetterOrEqualTo(Result.SUCCESS);

        if (runHarbormaster) {
            logger.info("harbormaster", "Sending Harbormaster BUILD_URL via PHID: " + phid);
            Task.Result sendUriResult = new SendHarbormasterUriTask(logger, diffClient, phid, buildUrl).run();

            if (sendUriResult != Task.Result.SUCCESS) {
                logger.info(LOGGING_TAG, "Unable to send BUILD_URL to Harbormaster");
            }

            if (harbormasterCoverage != null) {
                logger.info(
                        LOGGING_TAG,
                        String.format("Publishing coverage data to Harbormaster for %d files.", harbormasterCoverage.size())
                );
            }

            logger.info(
                    LOGGING_TAG,
                    String.format("Sending build result to Harbormaster with PHID %s, success: %s",
                            phid,
                            harbormasterSuccess
                    )
            );

            Task.Result result = new SendHarbormasterResultTask(logger, diffClient, phid, harbormasterSuccess, getCoverage()).run();
            if (result != Task.Result.SUCCESS) {
                return false;
            }
        } else {
            logger.info("uberalls", "Harbormaster integration not enabled for this build.");
            if (buildResult.isBetterOrEqualTo(Result.SUCCESS)) {
                commentAction = "resign";
            } else if (buildResult.isWorseOrEqualTo(Result.UNSTABLE)) {
                commentAction = "reject";
            }
        }
        return true;
    }

    /**
     * Process available coverage data into the Harbormaster coverage format
     * @param coverageProvider a provider for the coverage data
     */
    public void processCoverage(CoverageProvider coverageProvider) {
        if (coverageProvider == null) {
            logger.info(LOGGING_TAG, "No coverage provider available.");
            return;
        }
        Map<String, List<Integer>> lineCoverage = coverageProvider.readLineCoverage();
        if (lineCoverage == null || lineCoverage.isEmpty()) {
            logger.info(LOGGING_TAG, "No line coverage available to post to Harbormaster.");
            return;
        }

        harbormasterCoverage = new CoverageConverter().convert(lineCoverage);
    }

    public Map<String,String> getCoverage() {
        return harbormasterCoverage;
    }
}
