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

package com.uber.jenkins.phabricator.conduit;

import com.uber.jenkins.phabricator.PhabricatorPostbuildAction;
import com.uber.jenkins.phabricator.PhabricatorPostbuildSummaryAction;
import hudson.EnvVars;
import hudson.model.AbstractBuild;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class Differential {
    private JSONObject rawJSON;

    public Differential(JSONObject rawJSON) throws ArcanistUsageException, IOException, InterruptedException {
        this.rawJSON = rawJSON;
    }

    public String getRevisionID(boolean formatted) {
        String rawRevisionId = (String) this.rawJSON.get("revisionID");
        if (rawRevisionId == null || rawRevisionId.equals("")) {
            return null;
        }
        if (formatted) {
            return String.format("D%s", rawRevisionId);
        }
        return rawRevisionId;
    }

    /**
     * Get the summary message
     * @param phabricatorURL
     * @return
     */
    public String getSummaryMessage(String phabricatorURL) {
        return String.format("This was a build of <a href=\"%s\">%s</a> by %s &lt;%s&gt;",
                this.getPhabricatorLink(phabricatorURL),
                this.getRevisionID(true),
                this.rawJSON.get("authorName"), this.rawJSON.get("authorEmail"));
    }

    public String getPhabricatorLink(String phabricatorURL) {
        String revisionID = this.getRevisionID(true);
        try {
            URL base = new URL(phabricatorURL);
            return new URL(base, revisionID).toString();
        } catch (MalformedURLException e) {
            return String.format("%s%s", phabricatorURL, revisionID);
        }
    }

    public void decorate(AbstractBuild build, String phabricatorURL) {
        // Add a badge next to the build
        build.addAction(PhabricatorPostbuildAction.createShortText(
                this.getRevisionID(true),
                this.getPhabricatorLink(phabricatorURL)));
        // Add some long-form text
        this.createSummary(build).appendText(this.getSummaryMessage(phabricatorURL));
    }

    private PhabricatorPostbuildSummaryAction createSummary(final AbstractBuild build) {
        PhabricatorPostbuildSummaryAction action = new PhabricatorPostbuildSummaryAction("phabricator.png");
        build.addAction(action);
        return action;
    }

    /**
     * Get a build started message to post to phabricator
     * @param environment
     * @return
     */
    public String getBuildStartedMessage(EnvVars environment) {
        return String.format("Build started: %s (console: %sconsole)", environment.get("BUILD_URL"), environment.get("BUILD_URL"));
    }

    /**
     * Return the base commit of the diff
     * @return
     */
    public String getBaseCommit() {
        return (String) rawJSON.get("sourceControlBaseRevision");
    }

    /**
     * Return the local branch name
     * @return
     */
    public String getBranch() {
        Object branchName = rawJSON.get("branch");
        if (branchName instanceof JSONNull) {
            return "(none)";
        }
        try {
            return (String) branchName;
        } catch (ClassCastException e) {
            return "(unknown)";
        }
    }
}
