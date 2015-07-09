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
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Descriptor for {@link PhabricatorNotifier}. Used as a singleton.
 * The class is marked as public so that it can be accessed from views.
 *
 * <p>
 * See <tt>src/main/resources/hudson/plugins/hello_world/PhabricatorNotifier/*.jelly</tt>
 * for the actual HTML fragment for the configuration screen.
 */
@SuppressWarnings("UnusedDeclaration")
@Extension // This indicates to Jenkins that this is an implementation of an extension point.
public final class PhabricatorNotifierDescriptor extends BuildStepDescriptor<Publisher> {
    private String conduitURL;
    private String uberallsURL;
    private String commentFile;

    public String getCommentSize() {
        return commentSize;
    }

    public void setCommentSize(String commentSize) {
        this.commentSize = commentSize;
    }

    private String commentSize;

    public PhabricatorNotifierDescriptor() {
        super(PhabricatorNotifier.class);
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
        return "Post to Phabricator";
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
        // To persist global configuration information,
        // set that to properties and call save().
        req.bindJSON(this, formData.getJSONObject("uberalls"));
        save();
        return super.configure(req, formData);
    }

    public String getConduitURL() {
        if (conduitURL != null && !"".equals(conduitURL)) {
            return conduitURL;
        }
        return null;
    }

    public void setConduitURL(String value) {
        conduitURL = value;
    }

    public String getUberallsURL() {
        if (uberallsURL != null && !"".equals(uberallsURL)) {
            return uberallsURL;
        }
        return null;
    }

    public void setUberallsURL(String value) {
        uberallsURL = value;
    }

    public void setCommentFile(String commentFile) {
        this.commentFile = commentFile;
    }

    public String getCommentFile() {
        return commentFile;
    }
}
