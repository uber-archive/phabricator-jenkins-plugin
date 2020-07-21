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
import com.uber.jenkins.phabricator.conduit.HarbormasterClient.MessageType;
import com.uber.jenkins.phabricator.coverage.CodeCoverageMetrics;
import com.uber.jenkins.phabricator.coverage.CoverageConverter;
import com.uber.jenkins.phabricator.coverage.CoverageProvider;
import com.uber.jenkins.phabricator.lint.LintResult;
import com.uber.jenkins.phabricator.lint.LintResults;
import com.uber.jenkins.phabricator.tasks.PostCommentTask;
import com.uber.jenkins.phabricator.tasks.SendHarbormasterResultTask;
import com.uber.jenkins.phabricator.tasks.SendHarbormasterUriTask;
import com.uber.jenkins.phabricator.tasks.Task;
import com.uber.jenkins.phabricator.uberalls.UberallsClient;
import com.uber.jenkins.phabricator.unit.UnitResults;
import com.uber.jenkins.phabricator.unit.UnitTestProvider;
import com.uber.jenkins.phabricator.utils.CommonUtils;
import com.uber.jenkins.phabricator.utils.Logger;

import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import hudson.FilePath;
import hudson.model.Result;
import hudson.model.Run;

public class BuildResultProcessor {

    private static final String LOGGING_TAG = "process-build-result";

    private final Logger logger;
    private final Differential diff;
    private final DifferentialClient diffClient;
    private final String phid;
    private final String buildUrl;
    private final boolean runHarbormaster;
    private final FilePath workspace;
    private final Run<?, ?> build;
    private final CommentBuilder commenter;
    private String commentAction;
    private UnitResults unitResults;
    private Map<String, String> harbormasterCoverage;
    private LintResults lintResults;

    public BuildResultProcessor(
            Logger logger, Run<?, ?> build, FilePath workspace, Differential diff, DifferentialClient diffClient,
            String phid, CodeCoverageMetrics coverageResult, String buildUrl, boolean preserveFormatting,
            CoverageCheckSettings coverageCheckSettings) {
        this.logger = logger;
        this.diff = diff;
        this.diffClient = diffClient;
        this.phid = phid;
        this.buildUrl = buildUrl;
        this.build = build;
        this.workspace = workspace;

        this.commentAction = "none";
        this.commenter = new CommentBuilder(logger, coverageResult, buildUrl, preserveFormatting,
                coverageCheckSettings);
        this.runHarbormaster = !CommonUtils.isBlank(phid);
    }

    public Result getBuildResult() {
        // In Pipeline jobs, as long as no failure happens, the build status stays null.
        // The PhabricatorNotifier needs to interpret null as "not failed (yet)".
        if (this.build.getResult() == null) {
            return Result.SUCCESS;
        } else {
            return this.build.getResult();
        }
    }

    /**
     * Fetch parent coverage data from Uberalls, if available
     *
     * @param uberalls the client to the Uberalls instance
     * @return
     */
    public boolean processParentCoverage(UberallsClient uberalls) {
        // First add in info about the change in coverage, if applicable
        boolean passBuild = true;
        if (commenter.hasCoverageAvailable()) {
            if (uberalls.isConfigured()) {
                passBuild = commenter.processParentCoverage(uberalls.getParentCoverage(diff.getBaseCommit()),
                        diff.getBaseCommit(), diff.getBranch());
            } else {
                logger.info(LOGGING_TAG, "No Uberalls backend configured, skipping...");
            }
        } else {
            logger.info(LOGGING_TAG, "No line coverage found, skipping...");
        }
        return passBuild;
    }

    /**
     * Add build result data into the commenter
     *
     * @param commentOnSuccess whether a "success" should trigger a comment
     * @param commentWithConsoleLinkOnFailure whether a failure should trigger a console link
     */
    public void processBuildResult(boolean commentOnSuccess, boolean commentWithConsoleLinkOnFailure) {
        commenter.processBuildResult(getBuildResult(), commentOnSuccess, commentWithConsoleLinkOnFailure, runHarbormaster);
    }

    /**
     * Fetch a remote comment from the build workspace
     *
     * @param commentFile the path pattern of the file
     * @param commentSize the maximum number of bytes to read from the remote file
     */
    public void processRemoteComment(String commentFile, String commentSize) {
        RemoteFileFetcher commentFetcher = new RemoteFileFetcher(workspace, logger, commentFile, commentSize);
        try {
            String customComment = commentFetcher.getRemoteFile();
            commenter.addUserComment(customComment);
        } catch (InterruptedException e) {
            e.printStackTrace(logger.getStream());
        } catch (IOException e) {
            e.printStackTrace(logger.getStream());
        }
    }

    /**
     * Fetch remote lint violations from the build workspace and process
     *
     * @param lintFile the path pattern of the file
     * @param lintFileSize maximum number of bytes to read from the remote file
     */
    public void processLintResults(String lintFile, String lintFileSize) {
        RemoteFileFetcher lintFetcher = new RemoteFileFetcher(workspace, logger, lintFile, lintFileSize);
        try {
            String input = lintFetcher.getRemoteFile();
            if (input != null && input.length() > 0) {
                lintResults = new LintResults();
                BufferedReader reader = new BufferedReader(new StringReader(input));
                String lint = "";
                String line;
                while ((line = reader.readLine()) != null) {
                    lint += line;
                    try {
                        JSONObject json = JSONObject.fromObject(lint);
                        lintResults.add(LintResult.fromJsonObject(json));
                        lint = "";
                    } catch (JSONException e) {
                        e.printStackTrace(logger.getStream());
                    }
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace(logger.getStream());
        } catch (IOException e) {
            e.printStackTrace(logger.getStream());
        }
    }

    /**
     * Send a comment to the differential, if present
     *
     * @param commentWithConsoleLinkOnFailure whether we should provide a console link on failure
     */
    public void sendComment(boolean commentWithConsoleLinkOnFailure) {
        if (!commenter.hasComment()) {
            return;
        }

        if (commentWithConsoleLinkOnFailure && getBuildResult().isWorseOrEqualTo(hudson.model.Result.UNSTABLE)) {
            commenter.addBuildFailureMessage();
        } else {
            commenter.addBuildLink();
        }

        new PostCommentTask(logger, diffClient, diff.getRevisionID(false), commenter.getComment(), commentAction).run();
    }

    /**
     * Send Harbormaster result to Phabricator
     *
     * @return whether we were able to successfully send the result
     */
    public boolean processHarbormaster(boolean sendPartialResults) {
        MessageType messageType;
        if (sendPartialResults) {
            messageType = MessageType.work;
        } else if (getBuildResult().isBetterOrEqualTo(Result.SUCCESS)) {
            messageType = MessageType.pass;
        } else {
            messageType = MessageType.fail;
        }

        if (runHarbormaster) {
            logger.info("harbormaster", "Sending Harbormaster BUILD_URL via PHID: " + phid);
            Task.Result sendUriResult = new SendHarbormasterUriTask(logger, diffClient, phid, buildUrl).run();

            if (sendUriResult != Task.Result.SUCCESS) {
                logger.info(LOGGING_TAG, "Unable to send BUILD_URL to Harbormaster. " +
                        "This can be safely ignored, and is usually because it's already set.");
            }

            if (unitResults != null) {
                logger.info(
                        LOGGING_TAG,
                        String.format("Publishing unit results to Harbormaster for %d tests.",
                                unitResults.getResults().size())
                );
            }
            if (harbormasterCoverage != null) {
                logger.info(
                        LOGGING_TAG,
                        String.format("Publishing coverage data to Harbormaster for %d files.",
                                harbormasterCoverage.size())
                );
            }
            if (lintResults != null) {
                logger.info(
                        LOGGING_TAG,
                        String.format("Publishing lint results for %d violations",
                                lintResults.getResults().size())
                );
            }

            logger.info(
                    LOGGING_TAG,
                    String.format("Sending build result to Harbormaster with PHID %s, message type: %s",
                            phid,
                            messageType.name()
                    )
            );

            Task.Result result = new SendHarbormasterResultTask(
                    logger,
                    diffClient,
                    phid,
                    messageType,
                    unitResults,
                    harbormasterCoverage,
                    lintResults
            ).run();
            if (result != Task.Result.SUCCESS) {
                return false;
            }
        } else {
            logger.info("uberalls", "Harbormaster integration not enabled for this build.");
            if (getBuildResult().isBetterOrEqualTo(Result.SUCCESS)) {
                commentAction = "resign";
            } else if (getBuildResult().isWorseOrEqualTo(Result.UNSTABLE)) {
                commentAction = "reject";
            }
        }
        return true;
    }

    /**
     * Process unit test results from the test run
     *
     * @param unitProvider a provider for unit test results
     */
    public void processUnitResults(UnitTestProvider unitProvider) {
        if (unitProvider == null) {
            logger.info(LOGGING_TAG, "No unit provider available.");
            return;
        }
        if (!unitProvider.resultsAvailable()) {
            logger.info(LOGGING_TAG, "No unit results available.");
            return;
        }
        unitResults = unitProvider.getResults();
    }

    /**
     * Process available coverage data into the Harbormaster coverage format
     *
     * @param coverageProvider a provider for the coverage data
     */
    void processCoverage(CoverageProvider coverageProvider) {
        if (coverageProvider == null) {
            logger.info(LOGGING_TAG, "No coverage provider available.");
            return;
        }
        Map<String, List<Integer>> lineCoverage = coverageProvider.getLineCoverage();
        if (lineCoverage == null || lineCoverage.isEmpty()) {
            logger.info(LOGGING_TAG, "No line coverage available to post to Harbormaster.");
            return;
        }

        harbormasterCoverage = CoverageConverter.convert(lineCoverage);
    }

    public Map<String, String> getCoverage() {
        return harbormasterCoverage;
    }

    public UnitResults getUnitResults() {
        return unitResults;
    }

    public LintResults getLintResults() {
        return lintResults;
    }
}
