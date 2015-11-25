// Copyright (c) 2015 Uber
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

package com.uber.jenkins.phabricator.tasks;

import com.uber.jenkins.phabricator.conduit.ConduitAPIClient;
import com.uber.jenkins.phabricator.conduit.ConduitAPIException;
import com.uber.jenkins.phabricator.conduit.HarbormasterClient;
import com.uber.jenkins.phabricator.utils.Logger;

import java.io.IOException;

public class NonDifferentialHarbormasterTask extends Task {
    private final String phid;
    private final ConduitAPIClient conduit;
    private final hudson.model.Result buildResult;
    private final String buildUrl;
    private final HarbormasterClient harbormaster;

    /**
     * Task constructor.
     * @param logger The logger where logs go to.
     * @param conduitClient
     * @param result
     * @param buildUrl
     */
    public NonDifferentialHarbormasterTask(Logger logger, String phid, ConduitAPIClient conduitClient, hudson.model.Result result, String buildUrl) {
        super(logger);
        this.phid = phid;
        this.conduit = conduitClient;
        this.buildResult = result;
        this.buildUrl = buildUrl;

        this.harbormaster = new HarbormasterClient(conduit);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getTag() {
        return "non-differential-harbormaster";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setup() {
        // Do nothing
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void execute() {
        final boolean pass = buildResult.isBetterOrEqualTo(hudson.model.Result.SUCCESS);
        try {
            harbormaster.sendHarbormasterUri(phid, buildUrl);
            // Only send pass/fail, since coverage and unit aren't viewable outside of differentials
            harbormaster.sendHarbormasterMessage(phid, pass, null, null);
            result = Result.SUCCESS;
            return;
        } catch (ConduitAPIException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        result = Result.FAILURE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void tearDown() {
        // Do nothing
    }
}
