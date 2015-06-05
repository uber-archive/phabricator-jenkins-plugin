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

import com.uber.jenkins.phabricator.conduit.Differential;

import com.uber.jenkins.phabricator.uberalls.UberallsClient;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.plugins.cobertura.CoberturaBuildAction;
import hudson.plugins.cobertura.Ratio;
import hudson.plugins.cobertura.targets.CoverageMetric;
import hudson.plugins.cobertura.targets.CoverageResult;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;

import net.sf.json.JSONNull;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;

import java.io.*;

import static java.lang.Integer.parseInt;

public class PhabricatorNotifier extends Notifier {

    // Post a comment on success. Useful for lengthy builds.
    private final boolean commentOnSuccess;
    private final boolean uberallsEnabled;
    private final boolean commentWithConsoleLinkOnFailure;
    private final String commentFile;
    private final String commentSize;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public PhabricatorNotifier(boolean commentOnSuccess, boolean uberallsEnabled,
                               String commentFile, String commentSize, boolean commentWithConsoleLinkOnFailure) {
        this.commentOnSuccess = commentOnSuccess;
        this.uberallsEnabled = uberallsEnabled;
        this.commentFile = commentFile;
        this.commentSize = commentSize;
        this.commentWithConsoleLinkOnFailure = commentWithConsoleLinkOnFailure;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public final boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher,
                                 final BuildListener listener) throws InterruptedException, IOException {
        EnvVars environment = build.getEnvironment(listener);
        PrintStream logger = listener.getLogger();

        CoverageResult coverage = getUberallsCoverage(build, listener);
        if (coverage != null) {
            coverage.setOwner(build);
        }

        UberallsClient uberalls = new UberallsClient(getDescriptor().getUberallsURL(), environment, logger);
        boolean needsDecoration = environment.get(PhabricatorPlugin.WRAP_KEY, null) == null;

        boolean uberallsConfigured = !CommonUtils.isBlank(uberalls.getBaseURL());

        String diffID = environment.get(PhabricatorPlugin.DIFFERENTIAL_ID_FIELD);
        if (CommonUtils.isBlank(diffID)) {
            if (needsDecoration) {
                build.addAction(PhabricatorPostbuildAction.createShortText("master", null));
            }
            if (uberallsEnabled && coverage != null) {
                if (!uberallsConfigured) {
                    logger.println("[uberalls] enabled but no server configured. skipping.");
                } else {
                    String currentSHA = environment.get("GIT_COMMIT");
                    CodeCoverageMetrics codeCoverageMetrics = new CodeCoverageMetrics(coverage);

                    if (!CommonUtils.isBlank(currentSHA) && codeCoverageMetrics.isValid()) {
                        logger.println("[uberalls] sending coverage report for " + currentSHA + " as " +
                                codeCoverageMetrics.toString());
                        uberalls.recordCoverage(currentSHA, environment.get("GIT_BRANCH"), codeCoverageMetrics);
                    } else {
                        logger.println("[uberalls] no line coverage available for " + currentSHA);
                    }
                }
            }
            return this.ignoreBuild(logger, "No differential ID found.");
        }

        LauncherFactory starter = new LauncherFactory(launcher, environment, listener.getLogger(), build.getWorkspace());

        Differential diff = Differential.fromDiffID(diffID, starter);

        String revisionID = diff.getRevisionID();
        if (CommonUtils.isBlank(revisionID)) {
            return this.ignoreBuild(logger, "Unable to load revisionID from conduit for diff ID " + diffID);
        }

        String phid = environment.get(PhabricatorPlugin.PHID_FIELD);

        boolean runHarbormaster = phid != null && !"".equals(phid);
        boolean harbormasterSuccess = false;

        String comment = null;

        if (coverage != null) {
            Ratio lineCoverage = coverage.getCoverage(CoverageMetric.LINE);
            if (lineCoverage == null) {
                logger.println("[uberalls] no line coverage found, skipping...");
            } else {
                if (uberallsConfigured) {
                    comment = getCoverageComment(lineCoverage, uberalls, diff, logger, environment.get("BUILD_URL"));
                } else {
                    logger.println("[uberalls] no backend configured, skipping...");
                }
            }
        }

        if (build.getResult().isBetterOrEqualTo(Result.SUCCESS)) {
            harbormasterSuccess = true;
            if (comment == null && (this.commentOnSuccess || !runHarbormaster)) {
                comment = "Build is green";
            }
        } else if (build.getResult() == Result.UNSTABLE) {
            comment = "Build is unstable";
        } else if (build.getResult() == Result.FAILURE) {
            // TODO look for message here.
            if (!runHarbormaster || this.commentWithConsoleLinkOnFailure) {
                comment = "Build has FAILED";
            }
        } else if (build.getResult() == Result.ABORTED) {
            comment = "Build was aborted";
        } else {
            logger.print("Unknown build status " + build.getResult().toString());
        }

        String commentAction = "none";
        if (runHarbormaster) {
            logger.println("Sending build result to Harbormaster with PHID '" + phid + "', success: " + harbormasterSuccess);
            diff.harbormaster(phid, harbormasterSuccess);
        } else {
            logger.println("Harbormaster integration not enabled for this build.");
            if (build.getResult().isBetterOrEqualTo(Result.SUCCESS)) {
                commentAction = "resign";
            } else if (build.getResult().isWorseOrEqualTo(Result.UNSTABLE)) {
                commentAction = "reject";
            }
        }

        String customComment;
        try {
            customComment = getRemoteComment(build, logger, this.commentFile, this.commentSize);

            if (!CommonUtils.isBlank(customComment)) {
                if (comment == null) {
                    comment = String.format("```\n%s\n```\n\n", customComment);
                } else {
                    comment = String.format("%s\n\n```\n%s\n```\n", comment, customComment);
                }
            }
        } catch(InterruptedException e) {
            e.printStackTrace(logger);
        } catch (IOException e) {
            Util.displayIOException(e, listener);
        }

        if (comment != null) {
            boolean silent = false;
            if (this.commentWithConsoleLinkOnFailure && build.getResult().isWorseOrEqualTo(Result.UNSTABLE)) {
                comment += String.format("\n\nLink to build: %s", environment.get("BUILD_URL"));
                comment += String.format("\nSee console output for more information: %sconsole", environment.get("BUILD_URL"));
            } else {
                comment += String.format(" %s for more details.", environment.get("BUILD_URL"));
            }

            JSONObject result = diff.postComment(comment, silent, commentAction);
            if(!(result.get("errorMessage") instanceof JSONNull)) {
                logger.println("Get error " + result.get("errorMessage") + " with action " +
                        commentAction +"; trying again with action 'none'");
                diff.postComment(comment, silent, "none");
            }
        }

        return true;
    }

    private String getCoverageComment(Ratio lineCoverage, UberallsClient uberalls, Differential diff,
                                      PrintStream logger, String buildUrl) {
        Float lineCoveragePercent = lineCoverage.getPercentageFloat();
        logger.println("[uberalls] line coverage: " + lineCoveragePercent);
        logger.println("[uberalls] fetching coverage from " + uberalls.getBaseURL());
        CodeCoverageMetrics parentCoverage = uberalls.getParentCoverage(diff);
        if (parentCoverage == null) {
            logger.println("[uberalls] unable to find coverage for parent commit (" + diff.getBaseCommit() + ")");
            return null;
        } else {
            logger.println("[uberalls] found parent coverage as " + parentCoverage.getLineCoveragePercent());
            String coverageComment;
            float coverageDelta = lineCoveragePercent - parentCoverage.getLineCoveragePercent();
            String coverageDeltaDisplay = String.format("%.3f", coverageDelta);
            String lineCoverageDisplay = String.format("%.3f", lineCoveragePercent);
            if (coverageDelta > 0) {
                coverageComment = "Coverage increased (+" + coverageDeltaDisplay + "%) to " + lineCoverageDisplay + "%";
            } else if (coverageDelta < 0) {
                coverageComment = "Coverage decreased (" + coverageDeltaDisplay + "%) to " + lineCoverageDisplay + "%";
            } else {
                coverageComment = "Coverage remained the same (" + lineCoverageDisplay + "%)";
            }

            final String coberturaUrl = buildUrl + "cobertura";
            coverageComment += " when pulling **" + diff.getBranch() + "** into " +
                    parentCoverage.getSha1().substring(0, 7) + ". See " + coberturaUrl + " for the coverage report";

            return coverageComment;
        }
    }

    /**
     * Attempt to read a remote comment file
     * @param build the build
     * @param logger the logger
     * @return the contents of the string
     * @throws InterruptedException
     */
    private String getRemoteComment(AbstractBuild<?, ?> build, PrintStream logger, String commentFile, String maxSize) throws InterruptedException, IOException {
        if (CommonUtils.isBlank(commentFile)) {
            logger.println("[comment-file] no comment file configured");
            return null;
        }

        FilePath workspace = build.getWorkspace();
        FilePath[] src = workspace.list(commentFile);
        if (src.length == 0) {
            logger.println("[comment-file] no files found by path: '" + commentFile + "'");
            return null;
        }
        if (src.length > 1) {
            logger.println("[comment-file] Found multiple matches. Reading first only.");
        }

        FilePath source = src[0];

        int DEFAULT_COMMENT_SIZE = 1000;
        int maxLength = DEFAULT_COMMENT_SIZE;
        if (!CommonUtils.isBlank(maxSize)) {
            maxLength = parseInt(maxSize, 10);
        }
        if (source.length() < maxLength) {
            maxLength = (int)source.length();
        }
        byte[] buffer = new byte[maxLength];
        source.read().read(buffer, 0, maxLength);
        return new String(buffer);
    }

    private CoverageResult getUberallsCoverage(AbstractBuild<?, ?> build, BuildListener listener) {
        if (!build.getResult().isBetterOrEqualTo(Result.UNSTABLE) || !uberallsEnabled) {
            return null;
        }

        PrintStream logger = listener.getLogger();
        CoberturaBuildAction coberturaAction = build.getAction(CoberturaBuildAction.class);
        if (coberturaAction == null) {
            logger.println("[uberalls] no cobertura results found");
            return null;
        }
        return coberturaAction.getResult();
    }

    /**
     * Get the base phabricator URL
     * @return a phabricator URL
     */
    private String getPhabricatorURL() {
        return this.getDescriptor().getConduitURL();
    }

    private boolean ignoreBuild(PrintStream logger, String message) {
        logger.println(message);
        logger.println("Skipping Phabricator notification.");
        return true;
    }

    /**
     * These are used in the config.jelly file to populate the state of the fields
     */
    @SuppressWarnings("UnusedDeclaration")
    public boolean isCommentOnSuccess() {
        return commentOnSuccess;
    }

    @SuppressWarnings("UnusedDeclaration")
    public boolean isUberallsEnabled() {
        return uberallsEnabled;
    }

    @SuppressWarnings("UnusedDeclaration")
    public boolean isCommentWithConsoleLinkOnFailure() {
        return commentWithConsoleLinkOnFailure;
    }

    @SuppressWarnings("UnusedDeclaration")
    public String getCommentFile() {
        return commentFile;
    }

    // Overridden for better type safety.
    @Override
    public PhabricatorNotifierDescriptor getDescriptor() {
        return (PhabricatorNotifierDescriptor) super.getDescriptor();
    }
}
