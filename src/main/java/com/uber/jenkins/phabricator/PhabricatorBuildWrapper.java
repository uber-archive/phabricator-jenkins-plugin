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

import com.google.common.annotations.VisibleForTesting;
import com.uber.jenkins.phabricator.conduit.ConduitAPIClient;
import com.uber.jenkins.phabricator.conduit.ConduitAPIException;
import com.uber.jenkins.phabricator.conduit.Differential;
import com.uber.jenkins.phabricator.conduit.DifferentialClient;
import com.uber.jenkins.phabricator.credentials.ConduitCredentials;
import com.uber.jenkins.phabricator.tasks.ApplyPatchTask;
import com.uber.jenkins.phabricator.tasks.SendHarbormasterResultTask;
import com.uber.jenkins.phabricator.tasks.SendHarbormasterUriTask;
import com.uber.jenkins.phabricator.tasks.Task;
import com.uber.jenkins.phabricator.utils.CommonUtils;
import com.uber.jenkins.phabricator.utils.Logger;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Executor;
import hudson.model.Job;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.Result;
import hudson.model.Run;
import hudson.tasks.BuildWrapper;
import hudson.util.RunList;

import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class PhabricatorBuildWrapper extends BuildWrapper {
    private static final String CONDUIT_TAG = "conduit";
    private static final String DEFAULT_GIT_PATH = "git";
    private static final String DIFFERENTIAL_SUMMARY = "PHABRICATOR_DIFFERENTIAL_SUMMARY";
    private static final String DIFFERENTIAL_AUTHOR = "PHABRICATOR_DIFFERENTIAL_AUTHOR";
    private static final String DIFFERENTIAL_BASE_COMMIT = "PHABRICATOR_DIFFERENTIAL_BASE_COMMIT";
    private static final String DIFFERENTIAL_BRANCH = "PHABRICATOR_DIFFERENTIAL_BRANCH";

    private final boolean createCommit;
    private final boolean applyToMaster;
    private final boolean skipForcedClean;
    private final boolean createBranch;
    private final boolean patchWithForceFlag;
    private final boolean useRevisionId;

    @DataBoundConstructor
    public PhabricatorBuildWrapper(boolean createCommit, boolean applyToMaster,
                                   boolean skipForcedClean,
                                   boolean createBranch, boolean patchWithForceFlag,
                                   boolean useRevisionId) {
        this.createCommit = createCommit;
        this.applyToMaster = applyToMaster || useRevisionId; // Always use origin/master when useRevisionId is true
        this.skipForcedClean = skipForcedClean;
        this.createBranch = createBranch;
        this.patchWithForceFlag = patchWithForceFlag;
        this.useRevisionId = useRevisionId;
    }

    /** {@inheritDoc} */
    @Override
    public Environment setUp(AbstractBuild build,
                             Launcher launcher,
                             BuildListener listener) throws IOException, InterruptedException {
        EnvVars environment = build.getEnvironment(listener);
        Logger logger = new Logger(listener.getLogger());
        if (environment == null) {
            return this.ignoreBuild(logger, "No environment variables found?!");
        }

        final Map<String, String> envAdditions = new HashMap<String, String>();

        String phid = environment.get(PhabricatorPlugin.PHID_FIELD);
        String diffID = environment.get(PhabricatorPlugin.DIFFERENTIAL_ID_FIELD);
        if (CommonUtils.isBlank(diffID)) {
            this.addShortText(build);
            this.ignoreBuild(logger, "No differential ID found.");
            return new Environment(){};
        }

        LauncherFactory starter = new LauncherFactory(launcher, environment, listener.getLogger(), build.getWorkspace());

        ConduitAPIClient conduitClient;
        try {
            conduitClient = getConduitClient(build.getParent(), logger);
        } catch (ConduitAPIException e) {
            e.printStackTrace(logger.getStream());
            logger.warn(CONDUIT_TAG, e.getMessage());
            return null;
        }

        DifferentialClient diffClient = new DifferentialClient(diffID, conduitClient);

        if (!CommonUtils.isBlank(phid)) {
            logger.info("harbormaster", "Sending Harbormaster BUILD_URL via PHID: " + phid);
            String buildUrl = environment.get("BUILD_URL");
            Task.Result sendUriResult = new SendHarbormasterUriTask(logger, diffClient, phid, buildUrl).run();

            if (sendUriResult != Task.Result.SUCCESS) {
                logger.info("harbormaster", "Unable to send BUILD_URL to Harbormaster");
            }
        }

        Differential diff;
        try {
            diff = new Differential(diffClient.fetchDiff());
            diff.setCommitMessage(diffClient.getCommitMessage(diff.getRevisionID(false)));
            diff.decorate(build, this.getPhabricatorURL(build.getParent()));

            logger.info(CONDUIT_TAG, "Fetching differential from Conduit API");

            envAdditions.put(DIFFERENTIAL_AUTHOR, diff.getAuthorEmail());
            envAdditions.put(DIFFERENTIAL_BASE_COMMIT, diff.getBaseCommit());
            envAdditions.put(DIFFERENTIAL_BRANCH, diff.getBranch());
            envAdditions.put(DIFFERENTIAL_SUMMARY, diff.getCommitMessage());
        } catch (ConduitAPIException e) {
            e.printStackTrace(logger.getStream());
            logger.warn(CONDUIT_TAG, "Unable to fetch differential from Conduit API");
            logger.warn(CONDUIT_TAG, e.getMessage());
            return null;
        }

        String baseCommit = "origin/master";
        if (!applyToMaster) {
            baseCommit = diff.getBaseCommit();
        }

        final String conduitToken = this.getConduitToken(build.getParent(), logger);
        Task.Result result = new ApplyPatchTask(
                logger, starter, baseCommit, diffID, diff.getRevisionID(true), conduitToken, getArcPath(),
                DEFAULT_GIT_PATH, createCommit, skipForcedClean, createBranch,
                patchWithForceFlag, useRevisionId
        ).run();

        if (result != Task.Result.SUCCESS) {
            logger.warn("arcanist", "Error applying arc patch; got non-zero exit code " + result);
            Task.Result failureResult = new SendHarbormasterResultTask(logger, diffClient, phid, false, null, null, null).run();
            if (failureResult != Task.Result.SUCCESS) {
                // such failure, very broke
                logger.warn("arcanist", "Unable to notify harbormaster of patch failure");
            }
            // Indicate failure
            return null;
        }

        return new Environment(){
            @Override
            public void buildEnvVars(Map<String, String> env) {
                EnvVars envVars = new EnvVars(env);
                envVars.putAll(envAdditions);
                env.putAll(envVars);
            }
        };
    }

    /**
     * Abort running builds when new build referencing same revision is scheduled to run
     */
    @Override
    public void preCheckout(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException,
            InterruptedException {
        String abortOnRevisionId = getAbortOnRevisionId(build);
        // If ABORT_ON_REVISION_ID is available
        if (!CommonUtils.isBlank(abortOnRevisionId)) {
            // Create a cause of interruption
            PhabricatorCauseOfInterruption causeOfInterruption =
                    new PhabricatorCauseOfInterruption(build.getUrl());
            Run upstreamRun = getUpstreamRun(build);

            // Get the running builds that were scheduled before the current one
            RunList<AbstractBuild> runningBuilds = (RunList<AbstractBuild>) build.getProject().getBuilds();
            for (AbstractBuild runningBuild : runningBuilds) {
                Executor executor = runningBuild.getExecutor();
                Run runningBuildUpstreamRun = getUpstreamRun(runningBuild);

                // Ignore builds that were triggered by the same upstream build
                // Find builds triggered with the same ABORT_ON_REVISION_ID_FIELD
                if (runningBuild.isBuilding()
                        && runningBuild.number < build.number
                        && abortOnRevisionId.equals(getAbortOnRevisionId(runningBuild))
                        && (upstreamRun == null
                        || runningBuildUpstreamRun == null
                        || !upstreamRun.equals(runningBuildUpstreamRun))
                        && executor != null) {
                    // Abort the builds
                    executor.interrupt(Result.ABORTED, causeOfInterruption);
                }
            }
        }
    }

    private void addShortText(final AbstractBuild build) {
        build.addAction(PhabricatorPostbuildAction.createShortText("master", null));
    }

    private Environment ignoreBuild(Logger logger, String message) {
        logger.info("ignore-build", message);
        return new Environment(){};
    }

    private ConduitAPIClient getConduitClient(Job owner, Logger logger) throws ConduitAPIException {
        ConduitCredentials credentials = getConduitCredentials(owner);
        if (credentials == null) {
            throw new ConduitAPIException("No credentials configured for conduit");
        }
        return new ConduitAPIClient(credentials.getGateway(), getConduitToken(owner, logger));
    }

    private ConduitCredentials getConduitCredentials(Job owner) {
        return getDescriptor().getCredentials(owner);
    }

    @SuppressWarnings("UnusedDeclaration")
    public boolean isCreateCommit() {
        return createCommit;
    }

    @SuppressWarnings("UnusedDeclaration")
    public boolean isApplyToMaster() {
        return applyToMaster;
    }

    @SuppressWarnings("UnusedDeclaration")
    public boolean isCreateBranch() {
        return createBranch;
    }

    @SuppressWarnings("unused")
    public boolean isPatchWithForceFlag() {
        return patchWithForceFlag;
    }

    private String getPhabricatorURL(Job owner) {
        ConduitCredentials credentials = getConduitCredentials(owner);
        if (credentials != null) {
            return credentials.getUrl();
        }
        return null;
    }

    private String getConduitToken(Job owner, Logger logger) {
        ConduitCredentials credentials = getConduitCredentials(owner);
        if (credentials != null) {
            return credentials.getToken().getPlainText();
        }
        logger.warn("credentials", "No credentials configured.");
        return null;
    }

    /**
     * Return the path to the arcanist executable
     * @return a string, fully-qualified or not, could just be "arc"
     */
    private String getArcPath() {
        final String providedPath = getDescriptor().getArcPath();
        if (CommonUtils.isBlank(providedPath)) {
            return "arc";
        }
        return providedPath;
    }

    // Overridden for better type safety.
    @Override
    public PhabricatorBuildWrapperDescriptor getDescriptor() {
        return (PhabricatorBuildWrapperDescriptor)super.getDescriptor();
    }

    @VisibleForTesting
    static String getAbortOnRevisionId(AbstractBuild build) {
        ParametersAction parameters = build.getAction(ParametersAction.class);
        if (parameters != null) {
            ParameterValue parameterValue = parameters.getParameter(
                    PhabricatorPlugin.ABORT_ON_REVISION_ID_FIELD);
            if (parameterValue != null) {
                return (String) parameterValue.getValue();
            }
        }
        return null;
    }

    @VisibleForTesting
    static Run<?, ?> getUpstreamRun(AbstractBuild build) {
        CauseAction action = build.getAction(hudson.model.CauseAction.class);
        if (action != null) {
            Cause.UpstreamCause upstreamCause = action.findCause(hudson.model.Cause.UpstreamCause.class);
            if (upstreamCause != null) {
                return upstreamCause.getUpstreamRun();
            }
        }
        return null;
    }
}
