package com.uber.jenkins.phabricator;

import com.uber.jenkins.phabricator.credentials.ConduitCredentials;
import com.uber.jenkins.phabricator.utils.CommonUtils;
import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

@SuppressWarnings("UnusedDeclaration")
@Extension
public final class PhabricatorBuildStartNotifierDescriptor extends BuildStepDescriptor<Publisher> {

    private String credentialsID;
    private String uberallsURL;
    private boolean isBlueOceanEnabled;

    public PhabricatorBuildStartNotifierDescriptor() {
        super(PhabricatorBuildStartNotifier.class);
        load();
    }

    public boolean isApplicable(Class<? extends AbstractProject> aClass) {
        // Indicates that this builder can be used with all kinds of project types
        return true;
    }

    /**
     * This human readable name is used in the configuration screen.
     */
    public String getDisplayName() {
        return "Post to Phabricator, Build Starting";
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
        // To persist global configuration information, set that to properties and call save().
        req.bindJSON(this, formData.getJSONObject("phabstart"));
        save();
        return super.configure(req, formData);
    }

    @SuppressWarnings("unused")
    public ListBoxModel doFillCredentialsIDItems(
            @AncestorInPath Jenkins context,
            @QueryParameter String remoteBase) {
        return ConduitCredentialsDescriptor.doFillCredentialsIDItems(
                context);
    }

    public ConduitCredentials getCredentials(Job owner) {
        // This will always grab the credentials ID.
        return ConduitCredentialsDescriptor.getCredentials(owner, credentialsID);
    }

    public String getCredentialsID() {
        return credentialsID;
    }

    public void setCredentialsID(String credentialsID) {
        this.credentialsID = credentialsID;
    }

    public boolean getIsBlueOceanEnabled() {
        return isBlueOceanEnabled;
    }

    public void setIsBlueOceanEnabled(boolean value) {
        isBlueOceanEnabled = value;
    }
}
