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

import com.uber.jenkins.phabricator.utils.TestUtils;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class ApplyPatchTaskTest {
    private static final BiConsumer<ApplyPatchTask, String> setHgPath = ApplyPatchTask::setHgPath;
    private static final BiConsumer<ApplyPatchTask, String> setGitPath = ApplyPatchTask::setGitPath;
    private static final BiConsumer<ApplyPatchTask, String> setSvnPath = (t, p) -> { };

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Parameterized.Parameter
    public String scm;
    @Parameterized.Parameter(1)
    public BiConsumer<ApplyPatchTask, String> setScmPath;

    @Parameterized.Parameters
    public static Object[] configurations() {
        return new Object[] {
            new Object[] {"git", setGitPath},
            new Object[] {"hg", setHgPath},
            new Object[] {"svn", setSvnPath}
        };
    }

    @Test
    public void testApplyPatchWithValidArc() throws Exception {
        assertEquals(Task.Result.SUCCESS, getTask("echo", scm, "true").run());
    }

    @Test
    public void testApplyPatchWithInvalidArc() throws Exception {
        assertEquals(Task.Result.FAILURE, getTask("false", scm, "echo").run());
    }

    @Test
    public void testBothScmAndArcFailing() throws Exception {
        assertEquals(Task.Result.FAILURE, getTask("false", scm, "false").run());
    }

    private ApplyPatchTask getTask(String arcPath, String scmType, String scmPath) throws Exception {
        final ApplyPatchTask task = new ApplyPatchTask(
                TestUtils.getDefaultLogger(),
                TestUtils.createLauncherFactory(j),
                TestUtils.TEST_SHA,
                TestUtils.TEST_DIFFERENTIAL_ID,
                TestUtils.TEST_CONDUIT_URL,
                TestUtils.TEST_CONDUIT_TOKEN,
                arcPath,
                false, // createCommit
                false, // skipForcedClean
                false, // createBranch
                false,  // patchWithForceFlag
                scmType
        );

        setScmPath.accept(task, scmPath);

        return task;
    }
}
