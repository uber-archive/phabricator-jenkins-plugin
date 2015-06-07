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

import com.uber.jenkins.phabricator.conduit.ArcanistClient;
import com.uber.jenkins.phabricator.conduit.ArcanistUsageException;
import com.uber.jenkins.phabricator.conduit.Differential;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.tasks.BuildWrapper;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

public class PhabricatorBuildWrapper extends BuildWrapper {
    private final boolean createCommit;
    private final boolean applyToMaster;
    private final boolean uberDotArcanist;

    @DataBoundConstructor
    public PhabricatorBuildWrapper(boolean createCommit, boolean applyToMaster, boolean uberDotArcanist) {
        this.createCommit = createCommit;
        this.applyToMaster = applyToMaster;
        this.uberDotArcanist = uberDotArcanist;
    }

    @Override
    public Environment setUp(AbstractBuild build,
                             Launcher launcher,
                             BuildListener listener) throws IOException, InterruptedException {
        EnvVars environment = build.getEnvironment(listener);
        PrintStream logger = listener.getLogger();
        if (environment == null) {
            return this.ignoreBuild(logger, "No environment variables found?!");
        }

        final String arcPath = this.getArcPath();

        final Map<String, String> envAdditions = new HashMap<String, String>();
        envAdditions.put(PhabricatorPlugin.WRAP_KEY, "true");
        envAdditions.put(PhabricatorPlugin.ARCANIST_PATH, arcPath);

        final String conduitToken = this.getConduitToken();
        if (!CommonUtils.isBlank(conduitToken)) {
            // Make this available for the post-build action (PhabricatorNotifier.class)
            envAdditions.put(PhabricatorPlugin.CONDUIT_TOKEN, conduitToken);
        }

        String diffID = environment.get(PhabricatorPlugin.DIFFERENTIAL_ID_FIELD);
        if (CommonUtils.isBlank(diffID)) {
            this.addShortText(build);
            this.ignoreBuild(logger, "No differential ID found.");
        } else {
            LauncherFactory starter = new LauncherFactory(launcher, environment, listener.getLogger(), build.getWorkspace());

            if (uberDotArcanist) {
                int npmCode = starter.launch()
                        .cmds(Arrays.asList("npm", "install", "uber-dot-arcanist"))
                        .stdout(logger)
                        .join();

                if (npmCode != 0) {
                    logger.println("Got non-zero exit code installing uber-dot-arcanist from npm: " + npmCode);
                }
            }

            Differential diff;
            try {
                diff = new Differential(diffID, starter, conduitToken, arcPath);
                diff.decorate(build, this.getPhabricatorURL());

                logger.println("Applying patch for differential");

                // Post a silent notification
                diff.postComment(diff.getBuildStartedMessage(environment));
            } catch (ArcanistUsageException e) {
                logger.println("[arcanist] unable to apply patch");
                return null;
            }

            String baseCommit = "origin/master";
            if (!applyToMaster) {
                baseCommit = diff.getBaseCommit();
            }

            int resetCode = starter.launch()
                    .cmds(Arrays.asList("git", "reset", "--hard", baseCommit))
                    .stdout(logger)
                    .join();

            if (resetCode != 0) {
                logger.println("Got non-zero exit code resetting to base commit " + baseCommit + ": " + resetCode);
            }

            // Clean workspace, otherwise `arc patch` may fail
            starter.launch()
                    .stdout(logger)
                    .cmds(Arrays.asList("git", "clean", "-fd", "-f"))
                    .join();

            // Update submodules recursively.
            starter.launch()
                    .stdout(logger)
                    .cmds(Arrays.asList("git", "submodule", "update", "--init", "--recursive"))
                    .join();

            List<String> params = new ArrayList<String>(Arrays.asList("--nobranch", "--diff", diffID));
            if (!createCommit) {
                params.add("--nocommit");
            }

            ArcanistClient arc = new ArcanistClient(
                    arcPath,
                    "patch",
                    null,
                    conduitToken,
                    params.toArray(new String[params.size()]));

            int result = arc.callConduit(starter.launch(), logger);

            if (result != 0) {
                logger.println("[arcanist] Error applying arc patch; got non-zero exit code " + result);
                return null;
            }
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

    private Environment ignoreBuild(PrintStream logger, String message) {
        logger.println(message);
        return new Environment(){};
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

    public String getPhabricatorURL() {
        return this.getDescriptor().getConduitURL();
    }

    public String getConduitToken() {
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
