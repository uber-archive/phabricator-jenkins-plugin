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
import hudson.plugins.cobertura.CoberturaCoverageParser;
import hudson.plugins.cobertura.CoberturaPublisher;
import hudson.plugins.cobertura.Ratio;
import hudson.plugins.cobertura.targets.CoverageMetric;
import hudson.plugins.cobertura.targets.CoverageResult;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;

import net.sf.json.JSONNull;
import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintStream;

public class PhabricatorNotifier extends Notifier {

    // Post a comment on success. Useful for lengthy builds.
    private final boolean commentOnSuccess;
    private final boolean uberallsEnabled;
    private final String coberturaReportFile;
    private final boolean commentWithConsoleLinkOnFailure;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public PhabricatorNotifier(boolean commentOnSuccess, String coberturaReportFile, boolean uberallsEnabled,
                               boolean commentWithConsoleLinkOnFailure) {
        this.commentOnSuccess = commentOnSuccess;
        this.coberturaReportFile = coberturaReportFile;
        this.uberallsEnabled = uberallsEnabled;
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
        if (environment == null) {
            return this.ignoreBuild(logger, "No environment variables found?!");
        }

        CoverageResult coverage = getUberallsCoverage(build, listener);
        if (coverage != null) {
            coverage.setOwner(build);
        }

        UberallsClient uberalls = new UberallsClient(getDescriptor().getUberallsURL(), environment);
        boolean needsDecoration = environment.get(PhabricatorPlugin.WRAP_KEY, null) == null;

        boolean uberallsConfigured = !CommonUtils.isBlank(uberalls.getBaseURL());

        String diffID = environment.get(PhabricatorPlugin.DIFFERENTIAL_ID_FIELD);
        if (CommonUtils.isBlank(diffID)) {
            if (needsDecoration) {
                build.getActions().add(PhabricatorPostbuildAction.createShortText("master", null));
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

        if (needsDecoration) {
            diff.decorate(build, this.getPhabricatorURL());
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

        diff.setBuildFinished(build.getResult());

        if (comment != null) {
            boolean silent = false;

            String formattedMessage = String.format("%s.", comment);
            if (this.commentWithConsoleLinkOnFailure && build.getResult().isWorseOrEqualTo(Result.UNSTABLE)) {
                formattedMessage += String.format("\\n\\nLink to build: %s", environment.get("BUILD_URL"));
                formattedMessage += String.format("\\nSee console output for more information: %sconsole", environment.get("BUILD_URL"));
            } else {
                formattedMessage += String.format(" %s for more details.", environment.get("BUILD_URL"));
            }

            JSONObject result = diff.postComment(formattedMessage, silent, commentAction);
            if(!(result.get("errorMessage") instanceof JSONNull)) {
                logger.println("Get error " + result.get("errorMessage") + " with action " +
                        commentAction +"; trying again with action 'none'");
                diff.postComment(formattedMessage, silent, "none");
            }
        }

        return true;
    }

    private String getCoverageComment(Ratio lineCoverage, UberallsClient uberalls, Differential diff,
                                      PrintStream logger, String buildUrl) {
        Float lineCoveragePercent = lineCoverage.getPercentageFloat();
        logger.println("[uberalls] line coverage: " + lineCoveragePercent);
        CodeCoverageMetrics parentCoverage = uberalls.getParentCoverage(diff);
        if (parentCoverage == null) {
            logger.println("[uberalls] unable to find coverage for parent commit!");
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

    private CoverageResult getUberallsCoverage(AbstractBuild<?, ?> build, BuildListener listener) throws InterruptedException {
        if (!build.getResult().isBetterOrEqualTo(Result.UNSTABLE) || !uberallsEnabled) {
            return null;
        }

        PrintStream logger = listener.getLogger();

        logger.println("[uberalls] looking for coverage report in " + coberturaReportFile);

        final FilePath[] moduleRoots = build.getModuleRoots();
        final boolean multipleModuleRoots =
                moduleRoots != null && moduleRoots.length > 1;
        final FilePath moduleRoot = multipleModuleRoots ? build.getWorkspace() : build.getModuleRoot();
        final File buildCoberturaDir = build.getRootDir();
        FilePath buildTarget = new FilePath(buildCoberturaDir);

        FilePath[] reports = new FilePath[0];

        try {
            reports = moduleRoot.act(new CoberturaPublisher.ParseReportCallable(coberturaReportFile));
        } catch (IOException e) {
            Util.displayIOException(e, listener);
            e.printStackTrace(listener.fatalError("Unable to find coverage results"));
        }

        if (reports.length == 0) {
            logger.println("[uberalls] no coverage report found");
        }

        for (int i = 0; i < reports.length; i++) {
            final FilePath targetPath = new FilePath(buildTarget, "uberalls" + (i == 0 ? "" : i) + ".xml");
            try {
                reports[i].copyTo(targetPath);
            } catch (IOException e) {
                Util.displayIOException(e, listener);
                e.printStackTrace(listener.fatalError("Unable to copy coverage from " + reports[i] + " to " + buildTarget));
                build.setResult(Result.FAILURE);
            }
        }

        CoverageResult result = null;
        for (File coberturaXmlReport : build.getRootDir().listFiles(new CoberturaReportFilenameFilter())) {
            try {
                result = CoberturaCoverageParser.parse(coberturaXmlReport, result);
            } catch (IOException e) {
                Util.displayIOException(e, listener);
                e.printStackTrace(listener.fatalError("Unable to parse " + coberturaXmlReport));
            }
        }
        if (result == null) {
            logger.println("[uberalls] unable to parse any cobertura results");
        }
        return result;
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
    public String getCoberturaReportFile () {
        return coberturaReportFile;
    }

    @SuppressWarnings("UnusedDeclaration")
    public boolean isCommentWithConsoleLinkOnFailure() {
        return commentWithConsoleLinkOnFailure;
    }

    // Overridden for better type safety.
    @Override
    public PhabricatorNotifierDescriptor getDescriptor() {
        return (PhabricatorNotifierDescriptor) super.getDescriptor();
    }

    private static class CoberturaReportFilenameFilter implements FilenameFilter {
        public boolean accept(File dir, String name) {
            // TODO take this out of an anonymous inner class, create a singleton and use a Regex to match the name
            return name.startsWith("uberalls") && name.endsWith(".xml");
        }
    }
}
