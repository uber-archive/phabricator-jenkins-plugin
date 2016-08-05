// Copyright (c) 2016 Uber Technologies, Inc.
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
import com.uber.jenkins.phabricator.conduit.HarbormasterClient;
import com.uber.jenkins.phabricator.lint.LintResults;
import com.uber.jenkins.phabricator.unit.UnitResults;
import com.uber.jenkins.phabricator.utils.Logger;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.util.Map;

/**
 * Post warnings task.
 */
public class PostLintTask extends Task {
    private final String phid;
    private final boolean type;
    private final UnitResults unitResults;
    private final Map<String, String> coverage;
    private final LintResults lintResults;

    private final HarbormasterClient harbormaster;

    /**
     * PostLintTask constructor.
     *
     * @param logger the logger
     * @param conduit conduit client for harbormaster
     * @param lintResults JSON Object understood by HarborMaster sendmessage call
     */
    public PostLintTask(Logger logger, ConduitAPIClient conduit, String phid, boolean type, UnitResults unitResults, Map<String, String> coverage, LintResults lintResults) {
        super(logger);

        this.phid = phid;
        this.type = type;
        this.unitResults = unitResults;
        this.coverage = coverage;
        this.lintResults = lintResults;
        this.harbormaster = new HarbormasterClient(conduit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getTag() {
        return "post-lint";
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
        try {
            harbormaster.sendHarbormasterMessage(phid, type, unitResults, coverage, lintResults);
        } catch (ConduitAPIException e) {
            info("unable to post lint message to phabricator");
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
