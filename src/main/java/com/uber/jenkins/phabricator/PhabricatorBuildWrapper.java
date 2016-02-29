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
import com.uber.jenkins.phabricator.credentials.ConduitCredentials;
import com.uber.jenkins.phabricator.tasks.ApplyPatchTask;
import com.uber.jenkins.phabricator.tasks.CheckoutCommitTask;
import com.uber.jenkins.phabricator.tasks.SendHarbormasterResultTask;
import com.uber.jenkins.phabricator.tasks.SendHarbormasterUriTask;
import com.uber.jenkins.phabricator.tasks.Task;
import com.uber.jenkins.phabricator.utils.CommonUtils;
import com.uber.jenkins.phabricator.utils.Logger;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Job;
import hudson.model.Result;
import hudson.tasks.BuildWrapper;
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
    private final boolean showBuildStartedMessage;
    private final boolean skipForcedClean;
    private final boolean createBranch;
    private final boolean patchWithForceFlag;

    @DataBoundConstructor
    public PhabricatorBuildWrapper(boolean createCommit, boolean applyToMaster,
                                   boolean showBuildStartedMessage, boolean skipForcedClean,
                                   boolean createBranch, boolean patchWithForceFlag) {
        this.createCommit = createCommit;
        this.applyToMaster = applyToMaster;
        this.showBuildStartedMessage = showBuildStartedMessage;
        this.skipForcedClean = skipForcedClean;
        this.createBranch = createBranch;
        this.patchWithForceFlag = patchWithForceFlag;
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

        LauncherFactory starter = new LauncherFactory(launcher, environment, listener.getLogger(), build.getWorkspace());

        String phid = environment.get(PhabricatorPlugin.PHID_FIELD);
        String diffID = environment.get(PhabricatorPlugin.DIFFERENTIAL_ID_FIELD);
        String commitSha = environment.get(PhabricatorPlugin.COMMIT_ID_FIELD);

        if (CommonUtils.isBlank(diffID) && CommonUtils.isBlank(commitSha)) {
            this.addShortText(build);
            this.ignoreBuild(logger, "No differential ID found.");
            return new Environment(){};
        } else if (!CommonUtils.isBlank(commitSha)) {
            Task.Result result = new CheckoutCommitTask(logger, starter, DEFAULT_GIT_PATH, commitSha).run();
            if (result != Task.Result.SUCCESS) {
                logger.warn("phabricator", "Could not reset to given commit");
                build.setResult(Result.FAILURE);
            }
            return new Environment() {};
        }

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

            // Post a silent notification if option is enabled
            if (showBuildStartedMessage) {
                diffClient.postComment(diff.getRevisionID(false), diff.getBuildStartedMessage(environment));
                logger.warn("build-started", "[DEPRECATED] Build started message is deprecated. Consider upgrading Phabricator to get build URL as Harbormaster artifact");
            }
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
                logger, starter, baseCommit, diffID, conduitToken, getArcPath(),
                DEFAULT_GIT_PATH, createCommit, skipForcedClean, createBranch,
                patchWithForceFlag
        ).run();

        if (result != Task.Result.SUCCESS) {
            logger.warn("arcanist", "Error applying arc patch; got non-zero exit code " + result);
            Task.Result failureResult = new SendHarbormasterResultTask(logger, diffClient, phid, false, null, null).run();
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
        return new ConduitAPIClient(credentials.getUrl(), getConduitToken(owner, logger));
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
    public boolean isShowBuildStartedMessage() {
        return showBuildStartedMessage;
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
}
