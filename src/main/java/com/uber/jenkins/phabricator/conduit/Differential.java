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

import java.net.MalformedURLException;
import java.net.URL;

public class Differential {
    private static final String UNKNOWN_AUTHOR = "unknown";
    private static final String UNKNOWN_EMAIL = "unknown";

    private final JSONObject rawJSON;

    public Differential(JSONObject rawJSON) {
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
        PhabricatorPostbuildSummaryAction summary = createSummary(phabricatorURL);
        build.addAction(summary);

    }

    public PhabricatorPostbuildSummaryAction createSummary(String phabricatorURL) {
        return new PhabricatorPostbuildSummaryAction(
                "phabricator.png",
                getPhabricatorLink(phabricatorURL),
                getRevisionID(true),
                getAuthorName(),
                getAuthorEmail()
        );
    }

    private String getAuthorName() {
        return getOrElse(rawJSON, "authorName", UNKNOWN_AUTHOR);
    }

    private String getAuthorEmail() {
        return getOrElse(rawJSON, "authorEmail", UNKNOWN_EMAIL);
    }

    private String getOrElse(JSONObject json, String key, String orElse) {
        if (json.has(key)) {
            return json.getString(key);
        }
        return orElse;
    }

    /**
     * Get a build started message to post to phabricator
     * @param environment the environment variables for the build
     * @return the build started message
     */
    public String getBuildStartedMessage(EnvVars environment) {
        return String.format("Build started: %s (console: %sconsole)", environment.get("BUILD_URL"), environment.get("BUILD_URL"));
    }

    /**
     * Return the base commit of the diff
     * @return the base revision for git
     */
    public String getBaseCommit() {
        return (String) rawJSON.get("sourceControlBaseRevision");
    }

    /**
     * Return the local branch name
     * @return the name of the branch, or unknown
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
