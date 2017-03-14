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
import com.uber.jenkins.phabricator.utils.TestUtils;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.Assert.assertEquals;

public class ApplyPatchTaskTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testApplyPatchWithValidArc() throws Exception {
        ApplyPatchTask task = getTask("echo", "true");
        Task.Result result = task.run();
        assertEquals(Task.Result.SUCCESS, result);
    }

    @Test
    public void testApplyPatchWithInvalidArc() throws Exception {
        ApplyPatchTask task = getTask("false", "echo");
        Task.Result result = task.run();
        assertEquals(Task.Result.FAILURE, result);
    }

    @Test
    public void testBothGitAndArcFailing() throws Exception {
        ApplyPatchTask task = getTask("false", "false");
        assertEquals(Task.Result.FAILURE, task.run());
    }

    @Test
    public void testArcPatchWithoutRevisionId() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Logger logger = new Logger(new PrintStream(baos));
        ApplyPatchTask task = new ApplyPatchTask(
                logger,
                TestUtils.createLauncherFactory(j),
                TestUtils.TEST_SHA,
                TestUtils.TEST_DIFFERENTIAL_ID,
                TestUtils.TEST_REVISION_ID,
                TestUtils.TEST_CONDUIT_TOKEN,
                "echo",
                "true",
                false,
                false,
                false,
                false,
                false
        );
        task.run();
        assertEquals("patch --diff 123 --nocommit --nobranch --conduit-token=notarealtoken\n",
                new String(baos.toByteArray()));
    }

    @Test
    public void testArcPatchWithRevisionId() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Logger logger = new Logger(new PrintStream(baos));
        ApplyPatchTask task = new ApplyPatchTask(
                logger,
                TestUtils.createLauncherFactory(j),
                TestUtils.TEST_SHA,
                TestUtils.TEST_DIFFERENTIAL_ID,
                TestUtils.TEST_REVISION_ID,
                TestUtils.TEST_CONDUIT_TOKEN,
                "echo",
                "true",
                false,
                false,
                false,
                false,
                true
        );
        task.run();
        assertEquals("patch D456 --nocommit --nobranch --conduit-token=notarealtoken\n",
                new String(baos.toByteArray()));
    }

    private ApplyPatchTask getTask(String arcPath, String gitPath) throws Exception {
        return new ApplyPatchTask(
                TestUtils.getDefaultLogger(),
                TestUtils.createLauncherFactory(j),
                TestUtils.TEST_SHA,
                TestUtils.TEST_DIFFERENTIAL_ID,
                TestUtils.TEST_REVISION_ID,
                TestUtils.TEST_CONDUIT_TOKEN,
                arcPath,
                gitPath,
                false,
                false,
                false,
                false,
                false
        );
    }
}
