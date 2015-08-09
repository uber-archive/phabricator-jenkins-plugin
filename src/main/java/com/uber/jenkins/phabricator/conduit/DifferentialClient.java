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
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * DifferentialClient handles all interaction with conduit/arc for differentials
 */
public class DifferentialClient {
    private final String diffID;
    private final LauncherFactory launcher;
    private final String arcPath;
    private final String conduitToken;

    public DifferentialClient(String diffID, LauncherFactory launcher, String conduitToken, String arcPath) {
        this.diffID = diffID;
        this.launcher = launcher;
        this.conduitToken = conduitToken;
        this.arcPath = arcPath;
    }

    /**
     * Posts a comment to a differential
     * @param revisionID the revision ID (e.g. "D1234" without the "D")
     * @param message
     * @param silent whether or not to trigger an email
     * @param action phabricator comment action, e.g. 'resign', 'reject', 'none'
     */
    public JSONObject postComment(String revisionID, String message, boolean silent, String action) throws IOException, InterruptedException, ArcanistUsageException {
        Map params = new HashMap<String, String>();
        params.put("revision_id", revisionID);
        params.put("action", action);
        params.put("message", message);
        params.put("silent", silent);

        return this.callConduit("differential.createcomment", params);
    }

    public JSONObject fetchDiff() throws ArcanistUsageException, IOException, InterruptedException {
        Map params = new HashMap<String, String>();
        params.put("ids", new String[]{this.diffID});
        JSONObject query = this.callConduit("differential.querydiffs", params);
        JSONObject response;
        try {
            response = query.getJSONObject("response");
        } catch (JSONException e) {
            throw new ArcanistUsageException(
                    String.format("No 'response' object found in conduit call: (%s) %s",
                            e.getMessage(),
                            query.toString(2)));
        }
        try {
            return response.getJSONObject(diffID);
        } catch (JSONException e) {
            throw new ArcanistUsageException(
                    String.format("Unable to find '%s' key in response: (%s) %s",
                            diffID,
                            e.getMessage(),
                            response.toString(2)));

        }
    }

    /**
     * Sets a sendHarbormasterMessage build status
     * @param phid Phabricator object ID
     * @param pass whether or not the build passed
     * @throws IOException
     * @throws InterruptedException
     */
    public JSONObject sendHarbormasterMessage(String phid, boolean pass) throws IOException, InterruptedException, ArcanistUsageException {
        Map params = new HashMap<String, String>();
        params.put("type", pass ? "pass" : "fail");
        params.put("buildTargetPHID", phid);

        return this.callConduit("harbormaster.sendmessage", params);
    }

    /**
     * Post a comment on the differential
     * @param revisionID the revision ID (e.g. "D1234" without the "D")
     * @param message the string message to post
     * @return
     * @throws IOException
     * @throws InterruptedException
     * @throws ArcanistUsageException
     */
    public JSONObject postComment(String revisionID, String message) throws IOException, InterruptedException, ArcanistUsageException {
        return postComment(revisionID, message, true, "none");
    }

    protected JSONObject callConduit(String methodName, Map<String, String> params) throws IOException, InterruptedException, ArcanistUsageException {
        ArcanistClient arc = getArcanistClient(methodName, params);
        return arc.parseConduit(this.launcher.launch(), this.launcher.getStderr());
    }

    /**
     * Get a new arcanist client.
     * @param params parameters to pass to arcanist
     * @return a new ArcanistClient
     */
    protected ArcanistClient getArcanistClient(String methodName, Map<String, String> params) {
        return new ArcanistClient(
                this.arcPath,
                "call-conduit",
                params,
                conduitToken,
                methodName
        );
    }
}
