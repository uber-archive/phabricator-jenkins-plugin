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

import com.uber.jenkins.phabricator.conduit.*;
import com.uber.jenkins.phabricator.credentials.ConduitCredentials;
import com.uber.jenkins.phabricator.tasks.NonDifferentialBuildTask;
import com.uber.jenkins.phabricator.tasks.PostCommentTask;
import com.uber.jenkins.phabricator.uberalls.UberallsClient;
import com.uber.jenkins.phabricator.utils.CommonUtils;
import com.uber.jenkins.phabricator.utils.Logger;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Job;
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
    private static final String UBERALLS_TAG = "uberalls";
    private static final String CONDUIT_TAG = "conduit";
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
        final boolean needsDecoration = build.getActions(PhabricatorPostbuildAction.class).size() == 0;

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

        ConduitAPIClient conduitClient;
        try {
            conduitClient = getConduitClient(build.getParent(), logger);
        } catch (ConduitAPIException e) {
            e.printStackTrace(logger.getStream());
            logger.warn(CONDUIT_TAG, e.getMessage());
            return false;
        }

        DifferentialClient diffClient = new DifferentialClient(diffID, conduitClient);
        Differential diff;
        try {
            diff = new Differential(diffClient.fetchDiff());
        } catch (ConduitAPIException e) {
            logger.info(CONDUIT_TAG, "unable to fetch differential");
            return true;
        }

        if (needsDecoration) {
            diff.decorate(build, this.getPhabricatorURL(build.getParent()));
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
                logger.info(UBERALLS_TAG, "no backend configured, skipping...");
            }
        } else {
            logger.info(UBERALLS_TAG, "no line coverage found, skipping...");
        }

        // Add in comments about the build result
        commenter.processBuildResult(this.commentOnSuccess, this.commentWithConsoleLinkOnFailure, runHarbormaster);

        String commentAction = "none";
        if (runHarbormaster) {
            logger.info("harbormaster", "Sending build result to Harbormaster with PHID '" + phid + "', success: " + harbormasterSuccess);
            try {
                JSONObject result = diffClient.sendHarbormasterMessage(phid, harbormasterSuccess);
                if (result.containsKey("errorMessage") && !(result.get("errorMessage") instanceof JSONNull)) {
                    logger.info("harbormaster",
                            String.format("Error from Harbormaster: %s", result.getString("errorMessage")));
                    return false;
                }
            } catch (ConduitAPIException e) {
                logger.info(CONDUIT_TAG, "unable to post to harbormaster");
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
            if (commentWithConsoleLinkOnFailure &&
                    buildResult.isWorseOrEqualTo(hudson.model.Result.UNSTABLE)) {
                commenter.addBuildFailureMessage();
            } else {
                commenter.addBuildLink();
            }

            new PostCommentTask(logger, diffClient, diff.getRevisionID(false), commenter.getComment(), commentAction).run();
        }

        return true;
    }

    private ConduitAPIClient getConduitClient(Job owner, Logger logger) throws ConduitAPIException {
        ConduitCredentials credentials = getConduitCredentials(owner);
        if (credentials == null) {
            throw new ConduitAPIException("No credentials configured for conduit");
        }
        return new ConduitAPIClient(credentials.getUrl(), credentials.getToken().getPlainText());
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

    private ConduitCredentials getConduitCredentials(Job owner) {
        return getDescriptor().getCredentials(owner);
    }

    public String getConduitToken(Job owner, Logger logger) {
        ConduitCredentials credentials = getConduitCredentials(owner);
        if (credentials != null) {
            return credentials.getToken().getPlainText();
        }
        logger.warn("credentials", "No credentials configured.");
        return null;
    }

    public String getPhabricatorURL(Job owner) {
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
}
