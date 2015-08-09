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
import com.uber.jenkins.phabricator.tasks.Task;
import com.uber.jenkins.phabricator.utils.CommonUtils;
import com.uber.jenkins.phabricator.utils.Logger;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Job;
import hudson.tasks.BuildWrapper;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class PhabricatorBuildWrapper extends BuildWrapper {
    private static final String CONDUIT_TAG = "conduit";
    private static final String DEFAULT_GIT_PATH = "git";

    private final boolean createCommit;
    private final boolean applyToMaster;
    private final boolean uberDotArcanist;
    private final boolean showBuildStartedMessage;

    @DataBoundConstructor
    public PhabricatorBuildWrapper(boolean createCommit, boolean applyToMaster, boolean uberDotArcanist, boolean showBuildStartedMessage) {
        this.createCommit = createCommit;
        this.applyToMaster = applyToMaster;
        this.uberDotArcanist = uberDotArcanist;
        this.showBuildStartedMessage = showBuildStartedMessage;
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

        String diffID = environment.get(PhabricatorPlugin.DIFFERENTIAL_ID_FIELD);
        if (CommonUtils.isBlank(diffID)) {
            this.addShortText(build);
            this.ignoreBuild(logger, "No differential ID found.");
            return new Environment(){};
        }

        LauncherFactory starter = new LauncherFactory(launcher, environment, listener.getLogger(), build.getWorkspace());

        if (uberDotArcanist) {
            int npmCode = starter.launch()
                    .cmds(Arrays.asList("npm", "install", "uber-dot-arcanist"))
                    .stdout(logger.getStream())
                    .join();

            if (npmCode != 0) {
                logger.warn("uber-dot-arcanist", "Got non-zero exit code installing uber-dot-arcanist from npm: " + npmCode);
            }
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
        Differential diff;
        try {
            diff = new Differential(diffClient.fetchDiff());
            diff.decorate(build, this.getPhabricatorURL(build.getParent()));

            logger.info(CONDUIT_TAG, "Fetching differential from Conduit API");

            // Post a silent notification if option is enabled
            if (showBuildStartedMessage) {
                diffClient.postComment(diff.getRevisionID(false), diff.getBuildStartedMessage(environment));
            }
        } catch (ConduitAPIException e) {
            logger.warn(CONDUIT_TAG, "Unable to apply patch");
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
                DEFAULT_GIT_PATH, createCommit
        ).run();

        if (result != Task.Result.SUCCESS) {
            logger.warn("arcanist", "Error applying arc patch; got non-zero exit code " + result);
            return null;
        }

        return new Environment() {
            @Override
            public void buildEnvVars(Map<String, String> env) {
                // A little roundabout, but allows us to do overrides per
                // how EnvVars#override works (PATH+unique=/foo/bar)
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

    /**
     * This is used in config.jelly to populate the state of the checkbox
     */
    @SuppressWarnings("UnusedDeclaration")
    public boolean isCreateCommit() {
        return createCommit;
    }

    @SuppressWarnings("UnusedDeclaration")
    public boolean isApplyToMaster() {
        return applyToMaster;
    }

    @SuppressWarnings("UnusedDeclaration")
    public boolean isUberDotArcanist() {
        return uberDotArcanist;
    }

    @SuppressWarnings("UnusedDeclaration")
    public boolean isShowBuildStartedMessage() {
        return showBuildStartedMessage;
    }

    public String getPhabricatorURL(Job owner) {
        ConduitCredentials credentials = getConduitCredentials(owner);
        if (credentials != null) {
            return credentials.getUrl();
        }
        return this.getDescriptor().getConduitURL();
    }

    public String getConduitToken(Job owner, Logger logger) {
        ConduitCredentials credentials = getConduitCredentials(owner);
        if (credentials != null) {
            return credentials.getToken().getPlainText();
        }
        logger.warn("credentials", "No credentials configured. Falling back to deprecated configuration.");
        return this.getDescriptor().getConduitToken();
    }

    /**
     * Return the path to the arcanist executable
     * @return a string, fully-qualified or not, could just be "arc"
     */
    public String getArcPath() {
        final String providedPath = this.getDescriptor().getArcPath();
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
