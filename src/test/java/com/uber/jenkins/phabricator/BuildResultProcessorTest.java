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

import com.uber.jenkins.phabricator.conduit.ConduitAPIClient;
import com.uber.jenkins.phabricator.conduit.ConduitAPIException;
import com.uber.jenkins.phabricator.conduit.Differential;
import com.uber.jenkins.phabricator.conduit.DifferentialClient;
import com.uber.jenkins.phabricator.coverage.CodeCoverageMetrics;
import com.uber.jenkins.phabricator.coverage.CoverageProvider;
import com.uber.jenkins.phabricator.coverage.FakeCoverageProvider;
import com.uber.jenkins.phabricator.lint.LintResult;
import com.uber.jenkins.phabricator.utils.TestUtils;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.tasks.Builder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

public class BuildResultProcessorTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private BuildResultProcessor processor;
    private FreeStyleProject project;

    @Before
    public void setUp() throws IOException {
        processor = new BuildResultProcessor(
                TestUtils.getDefaultLogger(),
                mock(Run.class),
                new FilePath(new File("")),
                mock(Differential.class),
                mock(DifferentialClient.class),
                TestUtils.TEST_PHID,
                mock(CodeCoverageMetrics.class),
                TestUtils.TEST_BASE_URL,
                true,
                new CoverageCheckSettings(true, 0.0, 100.0)
        );
        project = j.createFreeStyleProject();
    }

    @Test
    public void testProcessCoverage() {
        CoverageProvider provider = new FakeCoverageProvider(TestUtils.getDefaultLineCoverage());
        processor.processCoverage(provider);
        assertNotNull(processor.getCoverage());
        assertNotNull(processor.getCoverage().get("file.go"));
        assertEquals("NCUC", processor.getCoverage().get("file.go"));
    }

    @Test
    public void testProcessEmptyCoverage() {
        CoverageProvider provider = new FakeCoverageProvider(Collections.emptyMap());
        processor.processCoverage(provider);
        assertNull(processor.getCoverage());
    }

    @Test
    public void testProcessNullProvider() {
        processor.processCoverage(null);
        assertNull(processor.getCoverage());
    }

    @Test
    public void testProcessNullUnitProvider() {
        processor.processUnitResults(null);
        assertNull(processor.getUnitResults());
    }

    @Test
    public void testProcessLintViolations() throws Exception {
        String content = "{\"App.java\":[{\"name\": \"Syntax Error\"," +
                "\"code\": \"EXAMPLE\"," +
                "\"severity\": \"error\"," +
                "\"line\": 17," +
                "\"char\": 3}]";
        final LintResult result = new LintResult("Syntax Error", "EXAMPLE", "error", "path/to/example", 17, 3, "");

        ConduitAPIClient conduitAPIClient = new ConduitAPIClient(null, null) {
            @Override
            public JSONObject perform(String action, JSONObject params) throws IOException, ConduitAPIException {
                if (action == "harbormaster.sendmessage") {

                    JSONObject json = (JSONObject) ((JSONArray) params.get("lint")).get(0);
                    JSONObject parsed = result.toHarbormaster();
                    assertNotNull(parsed);
                    assertNotNull(json);
                    for (String key : (Set<String>) params.keySet()) {
                        assertEquals("mismatch in expected json key: " + key, parsed.get(key), json.get(key));
                    }
                    return result.toHarbormaster();
                }
                return new JSONObject();
            }
        };

        runProcessLintViolationsTest(content, conduitAPIClient);
    }

    @Test
    public void testProcessLintViolationsWithNonJsonLines() throws Exception {
        String content =
                "{\"App.java\":[{ \"name\": \"PotentialLeak\", \"code\": \"\", \"severity\": \"error\", \"path\": \"Main.java\", \"line\": 21, \"char\": 5, \"description\": \"Potential leak detected.\n"
                        +
                        "Features should only be in memory when they are attached.\" },\n" +
                        "{ \"name\": \"PotentialLeak\", \"code\": \"\", \"severity\": \"error\", \"line\": 22, \"char\": 5, \"description\": \"Potential leak detected.\n"
                        +
                        "Features should only be in memory when they are attached.\" }]\n";

        ConduitAPIClient conduitAPIClient = new ConduitAPIClient(null, null) {
            @Override
            public JSONObject perform(String action, JSONObject params) throws IOException, ConduitAPIException {
                // Do nothing.
                return new JSONObject();
            }
        };

        BuildResultProcessor buildResultProcessor = runProcessLintViolationsTest(content, conduitAPIClient);
        assertEquals(2, buildResultProcessor.getLintResults().getResults().size());
    }

    private BuildResultProcessor runProcessLintViolationsTest(String lintFileContent, ConduitAPIClient conduitAPIClient)
            throws Exception {
        final String fileName = ".phabricator-lint";
        project.getBuildersList().add(echoBuilder(fileName, lintFileContent));
        FreeStyleBuild build = getBuild();

        BuildResultProcessor processor = new BuildResultProcessor(
                TestUtils.getDefaultLogger(),
                build,
                build.getWorkspace(),
                mock(Differential.class),
                new DifferentialClient(null, conduitAPIClient),
                TestUtils.TEST_PHID,
                mock(CodeCoverageMetrics.class),
                TestUtils.TEST_BASE_URL,
                true,
                new CoverageCheckSettings(true, 0.0, 100.0)
        );
        processor.processLintResults(fileName, "1000");
        processor.processHarbormaster();
        return processor;
    }

    private Builder echoBuilder(final String fileName, final String content) {
        return new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws
                    InterruptedException, IOException {
                build.getWorkspace().child(fileName).write(content, "UTF-8");
                return true;
            }
        };
    }

    private FreeStyleBuild getBuild() throws ExecutionException, InterruptedException {
        return project.scheduleBuild2(0).get();
    }
}
