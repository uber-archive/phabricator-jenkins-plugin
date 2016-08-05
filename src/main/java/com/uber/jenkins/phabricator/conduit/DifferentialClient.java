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

import com.uber.jenkins.phabricator.lint.LintResults;
import com.uber.jenkins.phabricator.unit.UnitResults;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.util.Map;

/**
 * DifferentialClient handles all interaction with conduit/arc for differentials
 */
public class DifferentialClient {
    private final String diffID;
    private final ConduitAPIClient conduit;

    public DifferentialClient(String diffID, ConduitAPIClient conduit) {
        this.diffID = diffID;
        this.conduit = conduit;
    }

    /**
     * Posts a comment to a differential
     * @param revisionID the revision ID (e.g. "D1234" without the "D")
     * @param message the content of the comment
     * @param silent whether or not to trigger an email
     * @param action phabricator comment action, e.g. 'resign', 'reject', 'none'
     * @return the Conduit API response
     * @throws IOException if there is a network error talking to Conduit
     * @throws ConduitAPIException if any error is experienced talking to Conduit
     */
    public JSONObject postComment(String revisionID, String message, boolean silent, String action) throws IOException, ConduitAPIException {
        JSONObject params = new JSONObject();
        params.element("revision_id", revisionID)
                .element("action", action)
                .element("message", message)
                .element("silent", silent);

        return this.callConduit("differential.createcomment", params);
    }

    /**
     * Fetch a differential from Conduit
     * @return the Conduit API response
     * @throws IOException if there is a network error talking to Conduit
     * @throws ConduitAPIException if any error is experienced talking to Conduit
     */
    public JSONObject fetchDiff() throws IOException, ConduitAPIException {
        JSONObject params = new JSONObject().element("ids", new String[]{diffID});
        JSONObject query = this.callConduit("differential.querydiffs", params);
        JSONObject response;
        try {
            response = query.getJSONObject("result");
        } catch (JSONException e) {
            throw new ConduitAPIException(
                    String.format("No 'result' object found in conduit call: (%s) %s",
                            e.getMessage(),
                            query.toString(2)));
        }
        try {
            return response.getJSONObject(diffID);
        } catch (JSONException e) {
            throw new ConduitAPIException(
                    String.format("Unable to find '%s' key in 'result': (%s) %s",
                            diffID,
                            e.getMessage(),
                            response.toString(2)));

        }
    }

    /**
     * Sets a sendHarbormasterMessage build status
     * @param phid Phabricator object ID
     * @param pass whether or not the build passed
     * @param unitResults the results from the unit tests
     * @param coverage the results from the coverage provider
     * @return the Conduit API response
     * @throws IOException if there is a network error talking to Conduit
     * @throws ConduitAPIException if any error is experienced talking to Conduit
     */
    public JSONObject sendHarbormasterMessage(String phid, boolean pass, UnitResults unitResults,
                                              Map<String, String> coverage,
                                              LintResults lintResults) throws ConduitAPIException, IOException {
        return new HarbormasterClient(conduit).sendHarbormasterMessage(phid, pass, unitResults, coverage, lintResults);
    }

    /**
     * Uploads a uri as an 'artifact' for Harbormaster to display
     * @param phid Phabricator object ID
     * @param buildUri Uri to display, presumably the jenkins builds
     * @return the Conduit API response
     * @throws IOException if there is a network error talking to Conduit
     * @throws ConduitAPIException if any error is experienced talking to Conduit
     */
    public JSONObject sendHarbormasterUri(String phid, String buildUri) throws ConduitAPIException, IOException {
        return new HarbormasterClient(conduit).sendHarbormasterUri(phid, buildUri);
    }

    /**
     * Post a comment on the differential
     * @param revisionID the revision ID (e.g. "D1234" without the "D")
     * @param message the string message to post
     * @return the Conduit API response
     * @throws IOException if there is a network error talking to Conduit
     * @throws ConduitAPIException if any error is experienced talking to Conduit
     */
    public JSONObject postComment(String revisionID, String message) throws ConduitAPIException, IOException {
        return postComment(revisionID, message, true, "none");
    }

    /**
     * Fetch the commit message for the revision. This isn't available on the diff, so it requires a separate query.
     * @param revisionID The ID of the revision, e.g. for "D123" this would be "123"
     * @return A \n-separated string of the commit message
     * @throws ConduitAPIException
     * @throws IOException
     */
    public String getCommitMessage(String revisionID) throws ConduitAPIException, IOException {
        JSONObject params = new JSONObject().element("revision_id", revisionID);
        JSONObject query = callConduit("differential.getcommitmessage", params);

        // NOTE: When you run this with `arc call-conduit dfferential.getcommitmessage` (from the command-line),
        // it comes back as "response". But it's "result" when running through this conduit API.
        return query.getString("result");
    }

    protected JSONObject callConduit(String methodName, JSONObject params) throws ConduitAPIException, IOException {
        return conduit.perform(methodName, params);
    }
}
