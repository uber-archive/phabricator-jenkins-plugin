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


import com.uber.jenkins.phabricator.utils.TestUtils;
import hudson.EnvVars;
import hudson.FilePath;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

import static org.junit.Assert.assertEquals;

public class LauncherFactoryTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private final EnvVars env = new EnvVars();

    @Test
    public void testLauncherFactoryIsSane() throws Exception {
        LauncherFactory factory = TestUtils.createLauncherFactory(j);
        assertEquals(factory.getStderr(), System.err);

        OutputStream out = new ByteArrayOutputStream();
        factory.launch().stdout(out).cmdAsSingleString("echo hello").join();
        assertEquals(out.toString(), "hello\n");
    }
}
