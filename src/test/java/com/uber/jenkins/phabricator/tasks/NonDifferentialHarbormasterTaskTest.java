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
import com.uber.jenkins.phabricator.utils.TestUtils;

import net.sf.json.JSONObject;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import hudson.model.Result;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NonDifferentialHarbormasterTaskTest {

    private ConduitAPIClient conduitClient;

    @Before
    public void setUp() {
        conduitClient = mock(ConduitAPIClient.class);
    }

    @Test
    public void testHappyPath() {
        assertEquals(Task.Result.SUCCESS, getResult());
    }

    @Test
    public void testConduitAPIException() throws Exception {
        when(conduitClient.perform(anyString(), any(JSONObject.class))).thenThrow(ConduitAPIException.class);

        assertEquals(Task.Result.FAILURE, getResult());
    }

    @Test
    public void testIOException() throws Exception {
        when(conduitClient.perform(anyString(), any(JSONObject.class))).thenThrow(IOException.class);

        assertEquals(Task.Result.FAILURE, getResult());
    }

    private Task.Result getResult() {
        return new NonDifferentialHarbormasterTask(
                TestUtils.getDefaultLogger(),
                TestUtils.TEST_PHID,
                conduitClient,
                Result.SUCCESS,
                TestUtils.TEST_BASE_URL,
                false
        ).run();
    }
}
