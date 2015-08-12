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

import hudson.model.FreeStyleBuild;
import hudson.model.Result;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class PhabricatorBuildWrapperTest extends BuildIntegrationTest {
    private PhabricatorBuildWrapper wrapper;

    @Before
    public void setUp() throws IOException {
        p = createProject();
        wrapper = new PhabricatorBuildWrapper(
                false,
                false,
                true
        );
    }

    @Test
    public void testNoParameterBuild() throws Exception {
        p.getBuildWrappersList().add(wrapper);

        FreeStyleBuild build = p.scheduleBuild2(0).get();
        Result result = build.getResult();
        assertSuccessfulBuild(result);
    }

    @Test
    public void testRoundTripConfiguration() throws Exception {
        p.getBuildWrappersList().add(wrapper);

        j.submit(j.createWebClient().getPage(p, "configure").getFormByName("config"));

        PhabricatorBuildWrapper after = p.getBuildWrappersList().get(PhabricatorBuildWrapper.class);
        j.assertEqualBeans(wrapper, after,
                "createCommit,applyToMaster,showBuildStartedMessage");
    }
}
