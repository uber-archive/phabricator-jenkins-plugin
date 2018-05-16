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

import hudson.model.Run;
import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

public class Differential {

    private static final String UNKNOWN_AUTHOR = "unknown";
    private static final String UNKNOWN_EMAIL = "unknown";

    private final JSONObject rawJSON;
    private String commitMessage;

    public Differential(JSONObject rawJSON) {
        this.rawJSON = rawJSON;
    }

    public String getDiffID() {
        String rawDiffId = (String) rawJSON.get("id");
        if (rawDiffId == null || rawDiffId.equals("")) {
            return null;
        }
        return rawDiffId;
    }

    public String getRevisionID(boolean formatted) {
        String rawRevisionId = (String) rawJSON.get("revisionID");
        if (rawRevisionId == null || rawRevisionId.equals("")) {
            return null;
        }
        if (formatted) {
            return String.format("D%s", rawRevisionId);
        }
        return rawRevisionId;
    }

    public String getPhabricatorLink(String phabricatorURL) {
        String revisionID = getRevisionID(true);
        try {
            URL base = new URL(phabricatorURL);
            return new URL(base, revisionID).toString();
        } catch (MalformedURLException e) {
            return String.format("%s%s", phabricatorURL, revisionID);
        }
    }

    public void decorate(Run<?, ?> build, String phabricatorURL) {
        // Add a badge next to the build
        build.addAction(PhabricatorPostbuildAction.createShortText(
                getRevisionID(true),
                getPhabricatorLink(phabricatorURL)));
        // Add some long-form text
        PhabricatorPostbuildSummaryAction summary = createSummary(phabricatorURL);
        build.addAction(summary);

    }

    public PhabricatorPostbuildSummaryAction createSummary(String phabricatorURL) {
        return new PhabricatorPostbuildSummaryAction(
                "phabricator.png",
                getPhabricatorLink(phabricatorURL),
                getDiffID(),
                getRevisionID(true),
                getAuthorName(),
                getAuthorEmail(),
                getCommitMessage()
        );
    }

    private String getAuthorName() {
        return getOrElse(rawJSON, "authorName", UNKNOWN_AUTHOR);
    }

    public String getAuthorEmail() {
        return getOrElse(rawJSON, "authorEmail", UNKNOWN_EMAIL);
    }

    private String getOrElse(JSONObject json, String key, String orElse) {
        if (json.has(key)) {
            return json.getString(key);
        }
        return orElse;
    }

    /**
     * Return the base commit of the diff
     *
     * @return the base revision for git
     */
    public String getBaseCommit() {
        return (String) rawJSON.get("sourceControlBaseRevision");
    }

    /**
     * Return the local branch name
     *
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

    /**
     * Get the differential commit message.
     *
     * @return the differential commit message.
     */
    public String getCommitMessage() {
        return commitMessage;
    }

    /**
     * Set the differential commit message.
     *
     * @param commitMesasge the differential commit message.
     */
    public void setCommitMessage(String commitMesasge) {
        this.commitMessage = commitMesasge;
    }

    /**
     * Get the list of changed files in the diff.
     *
     * @return the list of changed files in the diff.
     */
    public Set<String> getChangedFiles() {
        Set<String> changedFiles = new HashSet<String>();
        JSONArray changes = rawJSON.getJSONArray("changes");
        for (int i = 0; i < changes.size(); i++) {
            JSONObject change = changes.getJSONObject(i);
            String file = (String) change.get("currentPath");
            if (file != null) {
                changedFiles.add(file);
            }
        }
        return changedFiles;
    }
}
