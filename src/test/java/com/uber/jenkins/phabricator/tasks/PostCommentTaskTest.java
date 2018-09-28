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

import com.uber.jenkins.phabricator.conduit.ConduitAPIException;
import com.uber.jenkins.phabricator.conduit.DifferentialClient;
import com.uber.jenkins.phabricator.utils.Logger;
import com.uber.jenkins.phabricator.utils.TestUtils;

import net.sf.json.JSONObject;

import org.junit.Before;
import org.junit.Test;

import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

public class PostCommentTaskTest {

    private final String TEST_COMMENT = "They are selling like hotcakes!";
    private final String TEST_COMMENT_ACTION = "none";
    private final String TEST_REVISION_ID = "something";
    private Logger logger;
    private DifferentialClient differentialClient;

    @Before
    public void setup() {
        logger = TestUtils.getDefaultLogger();
        differentialClient = TestUtils.getDefaultDifferentialClient();
    }

    @Test
    public void testPostDifferentialFailed() throws Exception {
        doThrow(new ConduitAPIException("")).when(differentialClient).postComment(
                anyString(),
                anyString(),
                anyBoolean(),
                anyString()
        );

        PostCommentTask postCommentTask = new PostCommentTask(logger, differentialClient,
                TEST_REVISION_ID, TEST_COMMENT, TEST_COMMENT_ACTION);
        Task.Result result = postCommentTask.run();
        assert result == Task.Result.FAILURE;
    }

    @Test
    public void testPostDifferentialSuccess() throws Exception {
        doReturn(new JSONObject()).when(differentialClient).postComment(
                anyString(),
                anyString(),
                anyBoolean(),
                anyString()
        );

        assert new PostCommentTask(logger, differentialClient, TEST_REVISION_ID,
                TEST_COMMENT, TEST_COMMENT_ACTION).run() == Task.Result.SUCCESS;
    }
}
