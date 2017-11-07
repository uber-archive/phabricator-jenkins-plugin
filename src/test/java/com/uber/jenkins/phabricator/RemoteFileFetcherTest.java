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

package com.uber.jenkins.phabricator;

import com.uber.jenkins.phabricator.utils.Logger;
import com.uber.jenkins.phabricator.utils.TestUtils;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.tasks.Builder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.apache.commons.lang.StringUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RemoteFileFetcherTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private final Logger logger = TestUtils.getDefaultLogger();
    private FreeStyleProject project;

    @Before
    public void setUp() throws IOException {
        project = j.createFreeStyleProject();
    }

    @Test
    public void testNoCommentConfigured() throws Exception {
        FreeStyleBuild build = getBuild();
        RemoteFileFetcher fetcher = new RemoteFileFetcher(
                build.getWorkspace(),
                logger,
                "",
                "1000"
        );

        assertNull(fetcher.getRemoteFile());

        fetcher = new RemoteFileFetcher(
                build.getWorkspace(),
                logger,
                "non-existent",
                "1000"
        );

        assertNull(fetcher.getRemoteFile());
    }

    @Test
    public void testSingleFile() throws Exception {
        testWithContent("hello, world", "1000");
    }

    @Test
    public void testUTF8File() throws Exception {
        testWithContent("こんにちは世界", "1000");
    }

    @Test
    public void testLargeFile() throws Exception {
        testWithContent(StringUtils.repeat("a", 10000), "10000");
    }

    private void testWithContent(String content, String len) throws Exception {
        final String fileName = "just-a-test.txt";
        project.getBuildersList().add(echoBuilder(fileName, content));
        FreeStyleBuild build = getBuild();

        RemoteFileFetcher fetcher = new RemoteFileFetcher(
                build.getWorkspace(),
                logger,
                fileName,
                len
        );

        assertEquals(content, fetcher.getRemoteFile());

        fetcher = new RemoteFileFetcher(
                build.getWorkspace(),
                logger,
                "*.txt",
                len
        );

        assertEquals(content, fetcher.getRemoteFile());
    }

    private Builder echoBuilder(final String fileName, final String content) {
        return new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                build.getWorkspace().child(fileName).write(content, "UTF-8");
                return true;
            }
        };
    }

    private FreeStyleBuild getBuild() throws ExecutionException, InterruptedException {
        return project.scheduleBuild2(0).get();
    }
}
