package com.uber.jenkins.phabricator;

import com.uber.jenkins.phabricator.conduit.ConduitAPIClient;
import com.uber.jenkins.phabricator.conduit.ConduitAPIException;
import com.uber.jenkins.phabricator.conduit.DifferentialClient;
import com.uber.jenkins.phabricator.credentials.ConduitCredentials;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;

import java.io.IOException;
import javax.annotation.Nonnull;

import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * When using the Jenkins pipeline it doesn't make any sense to use BuildWrapper to start the
 * build. However, it is still useful to have something send the harbormaster URI at the
 * start of the build.
 */
public class PhabricatorBuildStartNotifier extends Publisher implements SimpleBuildStep {

    @DataBoundConstructor
    public PhabricatorBuildStartNotifier() { }

    @Override
    public void perform(@Nonnull Run<?, ?> build, @Nonnull FilePath workspace,
                        @Nonnull Launcher launcher, @Nonnull TaskListener listener)
            throws InterruptedException, IOException {
        EnvVars environment = build.getEnvironment(listener);
        final String diffID = environment.get(PhabricatorPlugin.DIFFERENTIAL_ID_FIELD);
        final String phid = environment.get(PhabricatorPlugin.PHID_FIELD);
        ConduitAPIClient conduitClient = getConduitClient(build.getParent());
        DifferentialClient diffClient = new DifferentialClient(diffID, conduitClient);
        String buildUrl;
        if (getDescriptor().getIsBlueOceanEnabled()) {
            buildUrl = environment.get("RUN_DISPLAY_URL");
        } else {
            buildUrl = environment.get("BUILD_URL");
        }
        try {
            diffClient.sendHarbormasterUri(phid, buildUrl);
        } catch (ConduitAPIException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    // Overridden for better type safety.
    @Override
    public PhabricatorBuildStartNotifierDescriptor getDescriptor() {
        return (PhabricatorBuildStartNotifierDescriptor) super.getDescriptor();
    }

    private ConduitCredentials getConduitCredentials(Job owner) {
        return getDescriptor().getCredentials(owner);
    }

    private ConduitAPIClient getConduitClient(Job owner) {
        ConduitCredentials credentials = getConduitCredentials(owner);
        if (credentials == null) {
            throw new RuntimeException("No credentials configured for conduit");
        }
        return new ConduitAPIClient(credentials.getGateway(), credentials.getToken().getPlainText());
    }
}
