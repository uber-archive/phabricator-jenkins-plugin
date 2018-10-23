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

import com.uber.jenkins.phabricator.conduit.ConduitAPIClient;
import com.uber.jenkins.phabricator.conduit.ConduitAPIException;
import com.uber.jenkins.phabricator.conduit.Differential;
import com.uber.jenkins.phabricator.conduit.DifferentialClient;
import com.uber.jenkins.phabricator.coverage.CodeCoverageMetrics;
import com.uber.jenkins.phabricator.coverage.CoverageProvider;
import com.uber.jenkins.phabricator.coverage.XmlCoverageProvider;
import com.uber.jenkins.phabricator.credentials.ConduitCredentials;
import com.uber.jenkins.phabricator.provider.InstanceProvider;
import com.uber.jenkins.phabricator.tasks.NonDifferentialBuildTask;
import com.uber.jenkins.phabricator.tasks.NonDifferentialHarbormasterTask;
import com.uber.jenkins.phabricator.tasks.Task;
import com.uber.jenkins.phabricator.uberalls.UberallsClient;
import com.uber.jenkins.phabricator.unit.UnitTestProvider;
import com.uber.jenkins.phabricator.utils.CommonUtils;
import com.uber.jenkins.phabricator.utils.Logger;

import org.kohsuke.stapler.DataBoundConstructor;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import jenkins.model.CauseOfInterruption;
import jenkins.model.InterruptedBuildAction;
import jenkins.tasks.SimpleBuildStep;

public class PhabricatorNotifier extends Notifier implements SimpleBuildStep {

    private static final String DEFAULT_XML_COVERAGE_REPORT_PATTERN = "**/coverage*.xml, **/cobertura*.xml, "
            + "**/jacoco*.xml";
    private static final CoverageReportFilenameFilter COVERAGE_FILENAME_FILTER = new CoverageReportFilenameFilter();

    private static final String ABORT_TAG = "abort";
    private static final String UBERALLS_TAG = "uberalls";
    private static final String COVERAGE_TAG = "coverage";
    private static final String CONDUIT_TAG = "conduit";
    // Post a comment on success. Useful for lengthy builds.
    private final boolean commentOnSuccess;
    private final boolean uberallsEnabled;
    private final CoverageCheckSettings coverageCheckSettings;
    private final boolean commentWithConsoleLinkOnFailure;
    private final boolean preserveFormatting;
    private final String commentFile;
    private final String commentSize;
    private final boolean customComment;
    private final boolean processLint;
    private final String lintFile;
    private final String lintFileSize;
    private final String coverageReportPattern;
    private transient UberallsClient uberallsClient;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public PhabricatorNotifier(
            boolean commentOnSuccess, boolean uberallsEnabled, boolean coverageCheck,
            double coverageThreshold, double minCoverageThreshold, String coverageReportPattern,
            boolean preserveFormatting, String commentFile, String commentSize,
            boolean commentWithConsoleLinkOnFailure, boolean customComment, boolean processLint,
            String lintFile, String lintFileSize) {
        this.commentOnSuccess = commentOnSuccess;
        this.uberallsEnabled = uberallsEnabled;
        this.coverageCheckSettings = new CoverageCheckSettings(coverageCheck, coverageThreshold, minCoverageThreshold);
        this.commentFile = commentFile;
        this.commentSize = commentSize;
        this.lintFile = lintFile;
        this.lintFileSize = lintFileSize;
        this.preserveFormatting = preserveFormatting;
        this.commentWithConsoleLinkOnFailure = commentWithConsoleLinkOnFailure;
        this.customComment = customComment;
        this.processLint = processLint;
        this.coverageReportPattern = coverageReportPattern;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public final void perform(
            final Run<?, ?> build, FilePath workspace, final Launcher launcher,
            final TaskListener listener) throws InterruptedException, IOException {
        EnvVars environment = build.getEnvironment(listener);
        Logger logger = new Logger(listener.getLogger());

        final String branch = environment.get("GIT_BRANCH");
        String gitUrl = environment.get("GIT_URL");
        if (gitUrl == null) {
            gitUrl = environment.get("GIT_URL_1");
        }

        final UberallsClient uberallsClient = getUberallsClient(logger, gitUrl, branch);

        final boolean needsDecoration = build.getActions(PhabricatorPostbuildAction.class).size() == 0;

        final String diffID = environment.get(PhabricatorPlugin.DIFFERENTIAL_ID_FIELD);
        final String phid = environment.get(PhabricatorPlugin.PHID_FIELD);
        final boolean isDifferential = !CommonUtils.isBlank(diffID);

        InterruptedBuildAction action = build.getAction(InterruptedBuildAction.class);
        if (action != null) {
            List<CauseOfInterruption> causes = action.getCauses();
            for (CauseOfInterruption cause : causes) {
                if (cause instanceof PhabricatorCauseOfInterruption) {
                    logger.warn(ABORT_TAG, "Skipping notification step since this build was interrupted"
                            + " by a newer build with the same differential revision");
                    return;
                }
            }
        }

        CoverageProvider coverageProvider;

        // Handle non-differential build invocations. If PHID is present but DIFF_ID is not, it means somebody is doing
        // a Harbormaster build on a commit rather than a differential, but still wants build status.
        // If DIFF_ID is present but PHID is not, it means somebody is doing a Differential build without Harbormaster.
        // So only skip build result processing if both are blank (e.g. master runs to update coverage data)
        if (CommonUtils.isBlank(phid) && !isDifferential) {
            if (needsDecoration) {
                build.addAction(PhabricatorPostbuildAction.createShortText(branch, null));
            }

            coverageProvider = getCoverageProvider(build, workspace, listener, Collections.emptySet());
            CodeCoverageMetrics coverageResult = null;
            if (coverageProvider != null) {
                coverageResult = coverageProvider.getMetrics();
            }

            NonDifferentialBuildTask nonDifferentialBuildTask = new NonDifferentialBuildTask(
                    logger,
                    uberallsClient,
                    coverageResult,
                    uberallsEnabled,
                    environment.get("GIT_COMMIT")
            );

            // Ignore the result.
            nonDifferentialBuildTask.run();
            return;
        }

        ConduitAPIClient conduitClient;
        try {
            conduitClient = getConduitClient(build.getParent());
        } catch (ConduitAPIException e) {
            e.printStackTrace(logger.getStream());
            logger.warn(CONDUIT_TAG, e.getMessage());
            throw new AbortException();
        }

        String whichBuildUrl;

        if (getDescriptor().getIsBlueOceanEnabled()) {
            whichBuildUrl = environment.get("RUN_DISPLAY_URL");
        } else {
            whichBuildUrl = environment.get("BUILD_URL");
        }

        // Still do finalization to prevent manipulation
        final String buildUrl = whichBuildUrl;

        if (!isDifferential) {
            // Process harbormaster for non-differential builds
            Task.Result result = new NonDifferentialHarbormasterTask(
                    logger,
                    phid,
                    conduitClient,
                    build.getResult(),
                    buildUrl
            ).run();
            if (result == Task.Result.SUCCESS) {
                return;
            } else {
                throw new AbortException();
            }
        }

        DifferentialClient diffClient = new DifferentialClient(diffID, conduitClient);
        Differential diff;
        try {
            diff = new Differential(diffClient.fetchDiff());
        } catch (ConduitAPIException e) {
            e.printStackTrace(logger.getStream());
            logger.warn(CONDUIT_TAG, "Unable to fetch differential from Conduit API");
            logger.warn(CONDUIT_TAG, e.getMessage());
            return;
        }

        if (needsDecoration) {
            diff.decorate(build, this.getPhabricatorURL(build.getParent()));
        }

        Set<String> includeFiles = diff.getChangedFiles();

        coverageProvider = getCoverageProvider(build, workspace, listener, includeFiles);
        CodeCoverageMetrics coverageResult = null;
        if (coverageProvider != null) {
            coverageResult = coverageProvider.getMetrics();
        }

        BuildResultProcessor resultProcessor = new BuildResultProcessor(
                logger,
                build,
                workspace,
                diff,
                diffClient,
                environment.get(PhabricatorPlugin.PHID_FIELD),
                coverageResult,
                buildUrl,
                preserveFormatting,
                coverageCheckSettings
        );

        if (uberallsEnabled) {
            boolean passBuildOnUberalls = resultProcessor.processParentCoverage(uberallsClient);
            if (!passBuildOnUberalls) {
                build.setResult(Result.FAILURE);
            }
        }

        // Add in comments about the build result
        resultProcessor.processBuildResult(commentOnSuccess, commentWithConsoleLinkOnFailure);

        // Process unit tests results to send to Harbormaster
        resultProcessor.processUnitResults(getUnitProvider(build, listener));

        // Read coverage data to send to Harbormaster
        resultProcessor.processCoverage(coverageProvider);

        if (processLint) {
            // Read lint results to send to Harbormaster
            resultProcessor.processLintResults(lintFile, lintFileSize);
        }

        // Fail the build if we can't report to Harbormaster
        if (!resultProcessor.processHarbormaster()) {
            throw new AbortException();
        }

        resultProcessor.processRemoteComment(commentFile, commentSize);

        resultProcessor.sendComment(commentWithConsoleLinkOnFailure);
    }

    protected UberallsClient getUberallsClient(Logger logger, String gitUrl, String branch) {
        if (uberallsClient != null) {
            return uberallsClient;
        }

        setUberallsClient(new UberallsClient(
                getDescriptor().getUberallsURL(),
                logger,
                gitUrl,
                branch
        ));
        return uberallsClient;
    }

    // Just for testing
    protected void setUberallsClient(UberallsClient client) {
        uberallsClient = client;
    }

    private ConduitAPIClient getConduitClient(Job owner) throws ConduitAPIException {
        ConduitCredentials credentials = getConduitCredentials(owner);
        if (credentials == null) {
            throw new ConduitAPIException("No credentials configured for conduit");
        }
        return new ConduitAPIClient(credentials.getGateway(), credentials.getToken().getPlainText());
    }

    /**
     * Get the coverage provider for the build
     *
     * @param build The current build
     * @param listener The build listener
     * @return The current coverage, if any
     */
    private CoverageProvider getCoverageProvider(
            Run<?, ?> build, FilePath workspace,
            TaskListener listener,
            Set<String> includeFiles) {
        Result buildResult;
        if (build.getResult() == null) {
            buildResult = Result.SUCCESS;
        } else {
            buildResult = build.getResult();
        }
        if (!buildResult.isBetterOrEqualTo(Result.UNSTABLE)) {
            return null;
        }

        copyCoverageToJenkinsMaster(build, workspace, listener);
        CoverageProvider coverageProvider = new XmlCoverageProvider(getCoverageReports(build), includeFiles);
        coverageProvider.computeCoverageIfNeeded();
        cleanupCoverageFilesOnJenkinsMaster(build);

        if (coverageProvider.hasCoverage()) {
            return coverageProvider;
        } else {
            Logger logger = new Logger(listener.getLogger());
            logger.info(UBERALLS_TAG, "No coverage results found");
            return null;
        }
    }

    private void copyCoverageToJenkinsMaster(Run<?, ?> build, FilePath workspace, TaskListener listener) {
        Logger logger = new Logger(listener.getLogger());
        final FilePath moduleRoot = workspace;
        final File buildCoberturaDir = build.getRootDir();
        FilePath buildTarget = new FilePath(buildCoberturaDir);

        String finalCoverageReportPattern = coverageReportPattern != null ? coverageReportPattern :
                DEFAULT_XML_COVERAGE_REPORT_PATTERN;

        if (moduleRoot != null) {
            try {
                int i = 0;
                for (FilePath report : moduleRoot.list(finalCoverageReportPattern)) {
                    final FilePath targetPath = new FilePath(buildTarget, "coverage" + (i == 0 ? "" : i) + ".xml");
                    report.copyTo(targetPath);
                    i++;
                }
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
                logger.warn(COVERAGE_TAG, "Unable to copy coverage to " + buildTarget);
            }
        }
    }

    private void cleanupCoverageFilesOnJenkinsMaster(Run<?, ?> build) {
        for (File report : getCoverageReports(build)) {
            report.delete();
        }
    }

    private Set<File> getCoverageReports(Run<?, ?> build) {
        Set<File> reports = new HashSet<>();

        File[] foundReports = build.getRootDir().listFiles(COVERAGE_FILENAME_FILTER);
        if (foundReports != null) {
            Collections.addAll(reports, foundReports);
        }
        return reports;
    }

    private UnitTestProvider getUnitProvider(Run<?, ?> build, TaskListener listener) {
        Logger logger = new Logger(listener.getLogger());

        UnitTestProvider unitProvider = InstanceProvider.getUnitTestProvider(logger);
        if (unitProvider == null) {
            return null;
        }
        unitProvider.setBuild(build);
        return unitProvider;
    }

    @SuppressWarnings("UnusedDeclaration")
    public boolean isCommentOnSuccess() {
        return commentOnSuccess;
    }

    @SuppressWarnings("UnusedDeclaration")
    public boolean isCustomComment() {
        return customComment;
    }

    @SuppressWarnings("UnusedDeclaration")
    public boolean isUberallsEnabled() {
        return uberallsEnabled;
    }

    @SuppressWarnings("UnusedDeclaration")
    public boolean isCoverageCheck() {
        return coverageCheckSettings.isCoverageCheckEnabled();
    }

    @SuppressWarnings("UnusedDeclaration")
    public boolean isCommentWithConsoleLinkOnFailure() {
        return commentWithConsoleLinkOnFailure;
    }

    @SuppressWarnings("UnusedDeclaration")
    public String getCommentFile() {
        return commentFile;
    }

    @SuppressWarnings("UnusedDeclaration")
    public String getCommentSize() {
        return commentSize;
    }

    @SuppressWarnings("UnusedDeclaration")
    public double getCoverageThreshold() {
        return coverageCheckSettings.getMaxCoverageDecreaseInPercent();
    }

    @SuppressWarnings("UnusedDeclaration")
    public double getMinCoverageThreshold() {
        return coverageCheckSettings.getMinCoverageInPercent();
    }

    @SuppressWarnings("UnusedDeclaration")
    public String getCoverageReportPattern() {
        return coverageReportPattern;
    }

    @SuppressWarnings("UnusedDeclaration")
    public boolean isPreserveFormatting() {
        return preserveFormatting;
    }

    @SuppressWarnings("UnusedDeclaration")
    public boolean isProcessLint() {
        return processLint;
    }

    @SuppressWarnings("UnusedDeclaration")
    public String getLintFile() {
        return lintFile;
    }

    @SuppressWarnings("UnusedDeclaration")
    public String getLintFileSize() {
        return lintFileSize;
    }

    private ConduitCredentials getConduitCredentials(Job owner) {
        return getDescriptor().getCredentials(owner);
    }

    private String getPhabricatorURL(Job owner) {
        ConduitCredentials credentials = getDescriptor().getCredentials(owner);
        if (credentials != null) {
            return credentials.getUrl();
        }
        return null;
    }

    // Overridden for better type safety.
    @Override
    public PhabricatorNotifierDescriptor getDescriptor() {
        return (PhabricatorNotifierDescriptor) super.getDescriptor();
    }

    private static class CoverageReportFilenameFilter implements FilenameFilter {

        @Override
        public boolean accept(File dir, String name) {
            return name.startsWith("coverage") && name.endsWith("xml");
        }
    }
}
