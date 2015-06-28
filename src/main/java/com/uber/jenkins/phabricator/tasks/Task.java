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

import com.uber.jenkins.phabricator.utils.Logger;

/**
 * Base task for all operations in the phabricator-jenkins plugin.
 */
public abstract class Task {

    /**
     * Task results.
     */
    public enum Result {
        SUCCESS,
        FAILURE,
        IGNORED,
        SKIPPED,
        UNKNWON
    };

    protected Logger logger;
    protected Result result = Result.UNKNWON;

    /**
     * Task constructor.
     * @param logger The logger where logs go to.
     */
    public Task(Logger logger) {
        this.logger = logger;

    }

    /**
     * Runs the task workflow.
     */
    public Result run() {
        setUp();
        execute();
        tearDown();

        return result;
    }

    /**
     * Logs the message.
     * @param message The message to log.
     */
    protected void info(String message) {
        logger.info(getTag(), message);
    }

    /**
     * Gets the task's tag.
     * @return A string representation of this task's tag.
     */
    protected abstract String getTag();

    /**
     * Sets up the environment before task execution.
     */
    protected abstract void setUp();

    /**
     * Executes the task workflow.
     */
    protected abstract void execute();

    /**
     * Tears down after task execution.
     */
    protected abstract void tearDown();
}
