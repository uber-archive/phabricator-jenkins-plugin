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

import com.uber.jenkins.phabricator.conduit.ArcanistUsageException;
import com.uber.jenkins.phabricator.conduit.Differential;
import com.uber.jenkins.phabricator.conduit.DifferentialClient;
import com.uber.jenkins.phabricator.tasks.NonDifferentialBuildTask;
import com.uber.jenkins.phabricator.uberalls.UberallsClient;
import com.uber.jenkins.phabricator.utils.CommonUtils;
import com.uber.jenkins.phabricator.utils.Logger;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.plugins.cobertura.CoberturaBuildAction;
import hudson.plugins.cobertura.targets.CoverageResult;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.PrintStream;

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
        Logger logger = new Logger(listener.getLogger());

        CoverageResult coverage = getUberallsCoverage(build, listener);
        if (coverage != null) {
            coverage.setOwner(build);
        }

        final String branch = environment.get("GIT_BRANCH");
        final UberallsClient uberalls = new UberallsClient(getDescriptor().getUberallsURL(), logger,
                environment.get("GIT_URL"), branch);
        final boolean needsDecoration = environment.get(PhabricatorPlugin.WRAP_KEY, null) == null;
        final String conduitToken = environment.get(PhabricatorPlugin.CONDUIT_TOKEN, null);
        final String arcPath = environment.get(PhabricatorPlugin.ARCANIST_PATH, "arc");
        final boolean uberallsConfigured = !CommonUtils.isBlank(uberalls.getBaseURL());
        final String diffID = environment.get(PhabricatorPlugin.DIFFERENTIAL_ID_FIELD);

        // Handle non-differential build invocations.
        if (CommonUtils.isBlank(diffID)) {
            if (needsDecoration) {
                build.addAction(PhabricatorPostbuildAction.createShortText(branch, null));
            }

            NonDifferentialBuildTask nonDifferentialBuildTask = new NonDifferentialBuildTask(logger, uberalls,
                    new CodeCoverageMetrics(coverage), uberallsEnabled,
                    environment.get("GIT_COMMIT"));

            // Ignore the result.
            nonDifferentialBuildTask.run();
            return true;
        }

        LauncherFactory starter = new LauncherFactory(launcher, environment, listener.getLogger(), build.getWorkspace());

        DifferentialClient diffClient = new DifferentialClient(diffID, starter, conduitToken, arcPath);
        Differential diff;
        try {
            diff = new Differential(diffClient.fetchDiff());
        } catch (ArcanistUsageException e) {
            logger.info("arcanist", "unable to fetch differential");
            return true;
        }

        String revisionID = diff.getRevisionID(true);
        if (CommonUtils.isBlank(revisionID)) {
            return this.ignoreBuild(logger.getStream(), "Unable to load revisionID from conduit for diff ID " + diffID);
        }

        String phid = environment.get(PhabricatorPlugin.PHID_FIELD);

        boolean runHarbormaster = !CommonUtils.isBlank(phid);
        Result buildResult = build.getResult();
        boolean harbormasterSuccess = buildResult.isBetterOrEqualTo(Result.SUCCESS);

        CommentBuilder commenter = new CommentBuilder(logger, buildResult, coverage, environment.get("BUILD_URL"));

        // First add in info about the change in coverage, if applicable
        if (commenter.hasCoverageAvailable()) {
            if (uberallsConfigured) {
                commenter.processParentCoverage(uberalls.getParentCoverage(diff), diff.getBranch());
            } else {
                logger.info("uberalls", "no backend configured, skipping...");
            }
        } else {
            logger.info("uberalls", "no line coverage found, skipping...");
        }

        // Add in comments about the build result
        commenter.processBuildResult(this.commentOnSuccess, this.commentWithConsoleLinkOnFailure, runHarbormaster);

        String commentAction = "none";
        if (runHarbormaster) {
            logger.info("uberalls", "Sending build result to Harbormaster with PHID '" + phid + "', success: " + harbormasterSuccess);
            try {
                diffClient.sendHarbormasterMessage(phid, harbormasterSuccess);
            } catch (ArcanistUsageException e) {
                logger.info("arcanist", "unable to post to sendHarbormasterMessage");
                return true;
            }
        } else {
            logger.info("uberalls", "Harbormaster integration not enabled for this build.");
            if (build.getResult().isBetterOrEqualTo(Result.SUCCESS)) {
                commentAction = "resign";
            } else if (build.getResult().isWorseOrEqualTo(Result.UNSTABLE)) {
                commentAction = "reject";
            }
        }

        RemoteCommentFetcher commentFetcher = new RemoteCommentFetcher(build.getWorkspace(), logger, this.commentFile, this.commentSize);
        try {
            String customComment = commentFetcher.getRemoteComment();
            commenter.addUserComment(customComment);
        } catch (InterruptedException e) {
            e.printStackTrace(logger.getStream());
        } catch (IOException e) {
            Util.displayIOException(e, listener);
        }

        if (commenter.hasComment()) {
            boolean silent = false;
            if (this.commentWithConsoleLinkOnFailure && build.getResult().isWorseOrEqualTo(Result.UNSTABLE)) {
                commenter.addBuildFailureMessage();
            } else {
                commenter.addBuildLink();
            }

            JSONObject result = null;
            String comment = commenter.getComment();
            try {
                result = diffClient.postComment(comment, silent, commentAction);
            } catch (ArcanistUsageException e) {
                logger.info("arcanist", "unable to post comment");
            }
            if (!(result.get("errorMessage") instanceof JSONNull)) {
                logger.info("arcanist", "Get error " + result.get("errorMessage") + " with action " +
                        commentAction + "; trying again with action 'none'");
                try {
                    diffClient.postComment(comment, silent, "none");
                } catch (ArcanistUsageException e) {
                    logger.info("arcanist", "unable to post comment");
                }
            }
        }

        return true;
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
