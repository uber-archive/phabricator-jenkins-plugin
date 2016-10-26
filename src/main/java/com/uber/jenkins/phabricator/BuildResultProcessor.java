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
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Result;
import hudson.model.Run;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;
import org.apache.commons.io.FilenameUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private UnitResults unitResults;
    private Map<String, String> harbormasterCoverage;
    private LintResults lintResults;

    public BuildResultProcessor(
            Logger logger, Run<?, ?> build, Differential diff, DifferentialClient diffClient,
            String phid, CodeCoverageMetrics coverageResult, String buildUrl, boolean preserveFormatting,
            FilePath workspace) {
        this.logger = logger;
        this.diff = diff;
        this.diffClient = diffClient;
        this.phid = phid;
        this.buildUrl = buildUrl;

        this.buildResult = build.getResult();
        this.workspace = workspace;

        this.commentAction = "none";
        this.commenter = new CommentBuilder(logger, build.getResult(), coverageResult, buildUrl, preserveFormatting);
        this.runHarbormaster = !CommonUtils.isBlank(phid);
    }

    /**
     * Fetch parent coverage data from Uberalls, if available
     *
     * @param uberalls the client to the Uberalls instance
     */
    public void processParentCoverage(UberallsClient uberalls) {
        // First add in info about the change in coverage, if applicable
        if (commenter.hasCoverageAvailable()) {
            if (uberalls.isConfigured()) {
                commenter.processParentCoverage(uberalls.getParentCoverage(diff.getBaseCommit()), diff.getBaseCommit(),
                        diff.getBranch());
            } else {
                logger.info(LOGGING_TAG, "No Uberalls backend configured, skipping...");
            }
        } else {
            logger.info(LOGGING_TAG, "No line coverage found, skipping...");
        }

    }

    /**
     * Add build result data into the commenter
     *
     * @param commentOnSuccess                whether a "success" should trigger a comment
     * @param commentWithConsoleLinkOnFailure whether a failure should trigger a console link
     */
    public void processBuildResult(boolean commentOnSuccess, boolean commentWithConsoleLinkOnFailure) {
        commenter.processBuildResult(commentOnSuccess, commentWithConsoleLinkOnFailure, runHarbormaster);
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
     * @param lintFile     the path pattern of the file
     * @param lintFileSize maximum number of bytes to read from the remote file
     */
    public void processLintResults(String lintFile, String lintFileSize) {
        RemoteFileFetcher lintFetcher = new RemoteFileFetcher(workspace, logger, lintFile, lintFileSize);
        try {
            String input = lintFetcher.getRemoteFile();
            if (input != null && input.length() > 0) {
                lintResults = new LintResults();
                BufferedReader reader = new BufferedReader(new StringReader(input));

                String lint;
                while ((lint = reader.readLine()) != null) {
                    JSONObject json = JSONObject.fromObject(lint);
                    lintResults.add(new LintResult(
                            (String) json.get("name"),
                            (String) json.get("code"),
                            (String) json.get("severity"),
                            (String) json.get("path"),
                            (Integer) json.get("line"),
                            (Integer) json.get("char"),
                            (String) json.get("description")));
                }
            }
        } catch (JSONException e) {
            e.printStackTrace(logger.getStream());
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

        if (commentWithConsoleLinkOnFailure && buildResult.isWorseOrEqualTo(hudson.model.Result.UNSTABLE)) {
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
    public boolean processHarbormaster() {
        final boolean harbormasterSuccess = buildResult.isBetterOrEqualTo(Result.SUCCESS);

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
                    String.format("Sending build result to Harbormaster with PHID %s, success: %s",
                            phid,
                            harbormasterSuccess
                    )
            );

            Task.Result result = new SendHarbormasterResultTask(
                    logger,
                    diffClient,
                    phid,
                    harbormasterSuccess,
                    unitResults,
                    harbormasterCoverage,
                    lintResults
            ).run();
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
    public void processCoverage(CoverageProvider coverageProvider, Set<String> include) {
        if (coverageProvider == null) {
            logger.info(LOGGING_TAG, "No coverage provider available.");
            return;
        }
        Map<String, List<Integer>> lineCoverage = coverageProvider.readLineCoverage();
        if (lineCoverage == null || lineCoverage.isEmpty()) {
            logger.info(LOGGING_TAG, "No line coverage available to post to Harbormaster.");
            return;
        }

        Set<String> includeFileNames = new HashSet<String>();
        for (String file : include) {
            includeFileNames.add(FilenameUtils.getBaseName(file));
        }

        Set<String> includedLineCoverage = new HashSet<String>();
        for (String file : lineCoverage.keySet()) {
            if (includeFileNames.contains(FilenameUtils.getBaseName(file))) {
                includedLineCoverage.add(file);
            }
        }

        lineCoverage.keySet().retainAll(includedLineCoverage);

        harbormasterCoverage = new CoverageConverter().convert(lineCoverage);
    }

    public Map<String, String> getCoverage() {
        return harbormasterCoverage;
    }

    public UnitResults getUnitResults() {
        return unitResults;
    }
}
