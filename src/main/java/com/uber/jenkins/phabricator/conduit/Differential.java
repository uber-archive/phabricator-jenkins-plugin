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

import com.uber.jenkins.phabricator.LauncherFactory;
import com.uber.jenkins.phabricator.PhabricatorPostbuildAction;
import com.uber.jenkins.phabricator.PhabricatorPostbuildSummaryAction;
import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.Result;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Differential {
    private final JSONObject rawJSON;
    private final LauncherFactory launcher;
    private final String diffID;

    /**
     * Instantiate a differential from a diff ID (not differential ID)
     * @param diffID
     * @param launcher
     * @return
     */
    public static Differential fromDiffID(String diffID, LauncherFactory launcher) throws IOException, InterruptedException {
        Map params = new HashMap<String, String>();
        params.put("ids", new String[]{diffID});
        ArcanistClient arc = new ArcanistClient("differential.querydiffs", params);

        Differential diff = new Differential(
            diffID,
            (JSONObject) ((JSONObject) arc.callConduit(launcher.launch(), launcher.getStderr()).get("response")).get(diffID),
            launcher
        );

        return diff;
    }

    Differential(String diffID, JSONObject rawJSON, LauncherFactory launcher) {
        this.diffID = diffID;
        this.rawJSON = rawJSON;
        this.launcher = launcher;
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

    public String getRevisionID() {
        return this.getRevisionID(true);
    }

    /**
     * Sets a harbormaster build status
     * @param phid Phabricator object ID
     * @param pass whether or not the build passed
     * @throws IOException
     * @throws InterruptedException
     */
    public void harbormaster(String phid, boolean pass) throws IOException, InterruptedException {
        Map params = new HashMap<String, String>();
        params.put("type", pass ? "pass" : "fail");
        params.put("buildTargetPHID", phid);
        ArcanistClient arc = new ArcanistClient("harbormaster.sendmessage", params);

        arc.callConduit(this.launcher.launch(), this.launcher.getStderr());
    }

    /**
     * Posts a comment to a differential
     * @param message
     * @param silent whether or not to trigger an email
     * @param action phabricator comment action, e.g. 'resign', 'reject', 'none'
     */
    public JSONObject postComment(String message, boolean silent, String action) throws IOException, InterruptedException {
        Map params = new HashMap<String, String>();
        params.put("revision_id", this.getRevisionID(false));
        params.put("action", action);
        params.put("message", this.escapeSpecialCharacters(message));
        params.put("silent", silent);

        ArcanistClient arc = new ArcanistClient("differential.createcomment", params);

        return arc.callConduit(this.launcher.launch(), this.launcher.getStderr());
    }

    public JSONObject postComment(String message, boolean silent) throws IOException, InterruptedException {
        return postComment(message, silent, "none");
    }

    /**
     * Don't mangle quotes because reasons
     * @param input
     * @return
     */
    private String escapeSpecialCharacters(final String input) {
        return input.replaceAll("\n", "\\\\n").replaceAll("\t", "    ");
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
        return String.format("%s%s", phabricatorURL, revisionID);
    }

    public void decorate(AbstractBuild build, String phabricatorURL) {
        // Add a badge next to the build
        build.getActions().add(PhabricatorPostbuildAction.createShortText(
                this.getRevisionID(true),
                this.getPhabricatorLink(phabricatorURL)));
        // Add some long-form text
        this.createSummary(build, "phabricator.png").appendText(this.getSummaryMessage(phabricatorURL), false);
    }

    private PhabricatorPostbuildSummaryAction createSummary(final AbstractBuild build, final String icon) {
        PhabricatorPostbuildSummaryAction action = new PhabricatorPostbuildSummaryAction(icon);
        build.getActions().add(action);
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
        } catch(ClassCastException e) {
            return "(unknown)";
        }
    }

    public void setDiffProperty(String name, String value) throws IOException, InterruptedException {
        Map params = new HashMap<String, String>();
        params.put("diff_id", this.diffID);
        params.put("name", name);
        params.put("data", String.format("\"%s\"", value));
        ArcanistClient arc = new ArcanistClient("differential.setdiffproperty", params);

        arc.callConduit(this.launcher.launch(), this.launcher.getStderr());
        return;
    }

    public void setBuildURL(EnvVars environment) throws IOException, InterruptedException {
        this.setDiffProperty("uber:build-url", environment.get("BUILD_URL"));
        this.setDiffProperty("uber:build-status", "PENDING");
    }

    public void setBuildFinished(Result result) throws IOException, InterruptedException {
        this.setDiffProperty("uber:build-status", result.toString());
    }
}
