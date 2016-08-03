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

package com.uber.jenkins.phabricator.tasks;

import com.uber.jenkins.phabricator.conduit.ConduitAPIClient;
import com.uber.jenkins.phabricator.conduit.ConduitAPIException;
import com.uber.jenkins.phabricator.conduit.DifferentialClient;
import com.uber.jenkins.phabricator.conduit.HarbormasterClient;
import com.uber.jenkins.phabricator.utils.Logger;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.util.List;

/**
 * Post warnings task.
 */
public class PostInlineTask extends Task {
    private final List<JSONObject> inlineContext;
    private final DifferentialClient differentialClient;
    private final String revisionID;
    /**
     * PostInlineTask constructor.
     *
     * @param logger the logger
     * @param differentialClient
     * @param inlineContext JSON Objects understood by HarborMaster
     */
    public PostInlineTask(Logger logger, DifferentialClient differentialClient, String revisionID, List<JSONObject> inlineContext) {
        super(logger);

        this.inlineContext = inlineContext;
        this.differentialClient = differentialClient;
        this.revisionID = revisionID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getTag() {
        return "post-inline";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setup() {
        // Do nothing.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void execute() {
        System.out.println("posting inline: ");
        try {
            for (JSONObject inline : inlineContext) {
                inline.element("revisionID", revisionID);
                differentialClient.postInlineComment(inline);
            }
        } catch (ConduitAPIException e) {
            info("unable to post inline context to phabricator");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void tearDown() {
        // Do nothing.
    }
}
