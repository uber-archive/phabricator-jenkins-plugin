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
import java.util.HashMap;
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
                                String phid, CodeCoverageMetrics coverageResult, String buildUrl) {
        this.logger = logger;
        this.diff = diff;
        this.diffClient = diffClient;
        this.phid = phid;
        this.buildUrl = buildUrl;

        this.buildResult = build.getResult();
        this.workspace = build.getWorkspace();

        this.commentAction = "none";
        this.commenter = new CommentBuilder(logger, build.getResult(), coverageResult, buildUrl);
        this.runHarbormaster = !CommonUtils.isBlank(phid);
    }

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

    public void processBuildResult(boolean commentOnSuccess, boolean commentWithConsoleLinkOnFailure) {
        commenter.processBuildResult(commentOnSuccess, commentWithConsoleLinkOnFailure, runHarbormaster);
    }

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

    public boolean processHarbormaster() {
        final boolean harbormasterSuccess = buildResult.isBetterOrEqualTo(Result.SUCCESS);

        if (runHarbormaster) {
            logger.info("harbormaster", "Sending Harbormaster BUILD_URL via PHID: " + phid);
            Task.Result sendUriResult = new SendHarbormasterUriTask(logger, diffClient, phid, buildUrl).run();

            if (sendUriResult != Task.Result.SUCCESS) {
                logger.info(LOGGING_TAG, "Unable to send BUILD_URL to Harbormaster");
            }

            if (getCoverage() != null) {
                logger.info(
                        LOGGING_TAG,
                        String.format("Publishing coverage data to Harbormaster for %d files.", getCoverage().size())
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
                logger.info(LOGGING_TAG, "Unable to post to harbormaster");
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

        harbormasterCoverage = convertCoverage(lineCoverage);
    }

    /**
     * Convert line coverage to the Harbormaster coverage format
     * @return The Harbormaster-formatted coverage
     */
    public Map<String, String> convertCoverage(Map<String, List<Integer>> lineCoverage) {
        Map<String, String> results = new HashMap<String, String>();
        for (Map.Entry<String, List<Integer>> entry : lineCoverage.entrySet()) {
            results.put(entry.getKey(), convertFileCoverage(entry.getValue()));
        }

        return results;
    }

    private String convertFileCoverage(List<Integer> lineCoverage) {
        StringBuilder sb = new StringBuilder();
        for (Integer line : lineCoverage) {
            // Can't use a case statement because NULL
            if (line == null) {
                sb.append('N');
            } else if (line == 0) {
                sb.append('U');
            } else {
                sb.append('C');
            }
        }
        return sb.toString();
    }

    public Map<String,String> getCoverage() {
        return harbormasterCoverage;
    }
}
