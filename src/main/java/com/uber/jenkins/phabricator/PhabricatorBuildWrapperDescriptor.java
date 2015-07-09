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

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.tasks.BuildWrapperDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;


@SuppressWarnings("UnusedDeclaration")
@Extension // This indicates to Jenkins that this is an implementation of an extension point.
public final class PhabricatorBuildWrapperDescriptor extends BuildWrapperDescriptor {
    private String conduitURL;
    private String conduitToken;
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
        return super.configure(req,formData);
    }

    public String getConduitURL() {
        return conduitURL;
    }

    public void setConduitURL(String value) {
        conduitURL = value;
    }

    public String getConduitToken() {
        return conduitToken;
    }

    public void setConduitToken(String conduitToken) {
        this.conduitToken = conduitToken;
    }

    public String getArcPath() {
        return arcPath;
    }

    public void setArcPath(String arcPath) {
        this.arcPath = arcPath;
    }
}
