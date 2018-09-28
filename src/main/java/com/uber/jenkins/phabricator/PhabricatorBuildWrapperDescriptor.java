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

import com.uber.jenkins.phabricator.credentials.ConduitCredentials;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

@SuppressWarnings("UnusedDeclaration")
@Extension
public final class PhabricatorBuildWrapperDescriptor extends BuildWrapperDescriptor {

    private String credentialsID;
    private String arcPath;

    public PhabricatorBuildWrapperDescriptor() {
        super(PhabricatorBuildWrapper.class);
        load();
    }

    @Override
    public boolean isApplicable(AbstractProject<?, ?> abstractProject) {
        return true;
    }

    /**
     * This human readable name is used in the configuration screen.
     */
    public String getDisplayName() {
        return "Apply Phabricator Differential";
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
        // To persist global configuration information,
        // set that to properties and call save().
        req.bindJSON(this, formData.getJSONObject("phabricator"));
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
        return ConduitCredentialsDescriptor.getCredentials(owner, credentialsID);
    }

    public String getCredentialsID() {
        return credentialsID;
    }

    public void setCredentialsID(String credentialsID) {
        this.credentialsID = credentialsID;
    }

    public String getArcPath() {
        return arcPath;
    }

    public void setArcPath(String arcPath) {
        this.arcPath = arcPath;
    }
}
