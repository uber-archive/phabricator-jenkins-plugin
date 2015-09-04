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

package com.uber.jenkins.phabricator;

import com.uber.jenkins.phabricator.conduit.Differential;
import com.uber.jenkins.phabricator.conduit.DifferentialClient;
import com.uber.jenkins.phabricator.coverage.CodeCoverageMetrics;
import com.uber.jenkins.phabricator.coverage.CoverageProvider;
import com.uber.jenkins.phabricator.coverage.FakeCoverageProvider;
import com.uber.jenkins.phabricator.utils.TestUtils;
import hudson.model.AbstractBuild;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

public class BuildResultProcessorTest {
    private BuildResultProcessor processor;

    @Before
    public void setUp() {
        processor = new BuildResultProcessor(
                TestUtils.getDefaultLogger(),
                mock(AbstractBuild.class),
                mock(Differential.class),
                mock(DifferentialClient.class),
                TestUtils.TEST_PHID,
                mock(CodeCoverageMetrics.class),
                TestUtils.TEST_BASE_URL,
                true
        );
    }

    @Test
    public void testProcessCoverage() throws Exception {
        CoverageProvider provider = new FakeCoverageProvider(TestUtils.getDefaultLineCoverage());
        processor.processCoverage(provider);
        assertNotNull(processor.getCoverage());
        assertNotNull(processor.getCoverage().get("file.go"));
        assertEquals("NCUC", processor.getCoverage().get("file.go"));
    }

    @Test
    public void testProcessEmptyCoverage() {
        CoverageProvider provider = new FakeCoverageProvider(null);
        processor.processCoverage(provider);
        assertNull(processor.getCoverage());
    }

    @Test
    public void testProcessNullProvider() {
        processor.processCoverage(null);
        assertNull(processor.getCoverage());
    }
}
