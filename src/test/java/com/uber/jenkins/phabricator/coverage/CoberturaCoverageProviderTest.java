package com.uber.jenkins.phabricator.coverage;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.plugins.cobertura.CoberturaBuildAction;
import hudson.plugins.cobertura.Ratio;
import hudson.plugins.cobertura.targets.CoverageMetric;
import hudson.plugins.cobertura.targets.CoverageResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CoberturaCoverageProviderTest {

    private static final String TEST_COVERAGE_FILE = "go-torch-coverage.xml";

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @WithoutJenkins
    @Test
    public void testConvertCobertura() {
        CoverageResult result = getMockResult();
        CodeCoverageMetrics metrics = CoberturaCoverageProvider.convertCobertura(result);
        assertEquals(75.0f, metrics.getLineCoveragePercent(), 0.0f);
        assertEquals(0.0f, metrics.getPackageCoveragePercent(), 0.0f);
    }

    @WithoutJenkins
    @Test
    public void testConvertNullCobertura() {
        assertNull(CoberturaCoverageProvider.convertCobertura(null));
    }

    @WithoutJenkins
    @Test
    public void testGetMetricsNullBuild() {
        CoberturaCoverageProvider provider = new CoberturaCoverageProvider(null, null, null, null);
        assertNull(provider.getMetrics());
    }

    @Test
    public void testGetMetricsNoResult() throws IOException {
        FreeStyleBuild build = getEmptyBuild();
        CoberturaCoverageProvider provider = new CoberturaCoverageProvider(build, build.getWorkspace(), null, null);
        assertNull(provider.getMetrics());
        assertFalse(provider.hasCoverage());
    }

    @Test
    public void testGetMetricsWithBuildActionResult() throws Exception {
        FreeStyleBuild build = getEmptyBuild();
        build.addAction(CoberturaBuildAction.load(
                getMockResult(),
                null,
                null,
                false,
                false,
                false,
                false,
                false,
                false,
                0
        ));
        CoberturaCoverageProvider provider = new CoberturaCoverageProvider(build, build.getWorkspace(), null, null);
        assertTrue(provider.hasCoverage());

        CodeCoverageMetrics metrics = provider.getMetrics();
        assertNotNull(metrics);
        assertEquals(75.0f, metrics.getLineCoveragePercent(), 0.0f);
    }

    @Test
    public void testGetMetricsWithoutBuildActionResult() throws Exception {
        Path testCoverageFile = Paths.get(getClass().getResource(TEST_COVERAGE_FILE).toURI());
        FreeStyleBuild build = getExecutedBuild();
        FileUtils.copyFile(testCoverageFile.toFile(), new File(build.getWorkspace().getRemote(), "coverage.xml"));

        CoberturaCoverageProvider provider = new CoberturaCoverageProvider(build, build.getWorkspace(), null, null);

        assertTrue(provider.hasCoverage());

        CodeCoverageMetrics metrics = provider.getMetrics();
        assertNotNull(metrics);
        assertEquals(89.69697f, metrics.getLineCoveragePercent(), 0.0f);
    }

    @Test
    public void testGetMetricsWithoutBuildActionResultDeletesFilesFromMasterAfter() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        FreeStyleBuild build = project.scheduleBuild2(0).get(100, TimeUnit.MINUTES);
        CoberturaCoverageProvider provider = new CoberturaCoverageProvider(build, build.getWorkspace(), null, null);
        Path testCoverageFile = Paths.get(getClass().getResource(TEST_COVERAGE_FILE).toURI());
        File cov = new File(build.getWorkspace().getRemote(), "coverage.xml");
        FileUtils.copyFile(testCoverageFile.toFile(), cov);
        assertTrue(provider.hasCoverage());

        CodeCoverageMetrics metrics = provider.getMetrics();
        assertNotNull(metrics);
        assertFalse(new File(project.getLastBuild().getRootDir(), "coverage.xml").exists());
    }

    @Test
    public void testGetLineCoverageNull() throws Exception {
        FreeStyleBuild build = getExecutedBuild();
        CoberturaCoverageProvider provider = new CoberturaCoverageProvider(build, build.getWorkspace(), null, null);
        assertNull(provider.readLineCoverage());
    }

    @Test
    public void testGetLineCoverageWithFile() throws Exception {
        FreeStyleBuild build = getExecutedBuild();
        File coverageFile = new File(build.getWorkspace().getRemote(), "coverage0.xml");
        InputStream in = getClass().getResourceAsStream("go-torch-coverage.xml");
        OutputStream out = new BufferedOutputStream(new FileOutputStream(coverageFile));
        IOUtils.copy(in, out);
        out.close();

        CoberturaCoverageProvider provider = new CoberturaCoverageProvider(build, build.getWorkspace(), null, null);
        Map<String, List<Integer>> coverage = provider.readLineCoverage();

        assertNotNull(coverage);
        assertEquals(1, coverage.get("github.com/uber/go-torch/visualization/visualization.go").get(66).longValue());
    }

    private CoverageResult getMockResult() {
        Ratio ratio = Ratio.create(75.0f, 100.0f);
        CoverageResult result = mock(CoverageResult.class);
        when(result.getCoverage(CoverageMetric.LINE)).thenReturn(ratio);
        return result;
    }

    private FreeStyleBuild getEmptyBuild() throws IOException {
        return new FreeStyleBuild(j.createFreeStyleProject());
    }

    private FreeStyleBuild getExecutedBuild() throws Exception {
        return j.createFreeStyleProject().scheduleBuild2(0).get(100, TimeUnit.MINUTES);
    }
}
