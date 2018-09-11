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

package com.uber.jenkins.phabricator.tasks;

import com.uber.jenkins.phabricator.conduit.ConduitAPIException;
import com.uber.jenkins.phabricator.conduit.DifferentialClient;
import com.uber.jenkins.phabricator.utils.Logger;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;

import java.io.IOException;

public class SendHarbormasterUriTask extends Task {

    private final DifferentialClient diffClient;
    private final String phid;
    private final String buildUri;

    public SendHarbormasterUriTask(Logger logger, DifferentialClient diffClient, String phid, String buildUri) {
        super(logger);
        this.diffClient = diffClient;
        this.phid = phid;
        this.buildUri = buildUri;
    }

    @Override
    protected String getTag() {
        return "send-harbormaster-uri";
    }

    @Override
    protected void setup() {
        // Do nothing
    }

    @Override
    protected void execute() {
        try {
            JSONObject result = diffClient.sendHarbormasterUri(phid, buildUri);
            if (result.containsKey("error_info") && !(result.get("error_info") instanceof JSONNull)) {
                info(String.format("Harbormaster declined URI artifact: %s", result.getString("error_info")));
                this.result = Result.FAILURE;
            } else {
                this.result = Result.SUCCESS;
            }
        } catch (ConduitAPIException e) {
            printStackTrace(e);
            this.result = Result.FAILURE;
        } catch (IOException e) {
            printStackTrace(e);
            this.result = Result.FAILURE;
        }
    }

    @Override
    protected void tearDown() {
        // Do nothing
    }
}
