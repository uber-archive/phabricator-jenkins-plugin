package com.uber.jenkins.phabricator.coverage;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;
import org.xml.sax.SAXException;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CoberturaCoverageProviderTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    private CoberturaCoverageProvider provider;

    @WithoutJenkins
    @Before
    public void setUp() {
        provider = new CoberturaCoverageProvider();
    }

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
        assertNull(provider.getMetrics());
    }

    @Test
    public void testGetMetricsNoResult() throws IOException {
        FreeStyleBuild build = getBuild();
        provider.setBuild(build);
        assertNull(provider.getMetrics());
        assertFalse(provider.hasCoverage());
    }

    @Test
    public void testGetMetricsWithResult() throws IOException {
        FreeStyleBuild build = getBuild();
        build.addAction(CoberturaBuildAction.load(
                build,
                getMockResult(),
                null,
                null,
                false,
                false,
                false,
                false,
                false
        ));
        provider.setBuild(build);
        assertTrue(provider.hasCoverage());

        CodeCoverageMetrics metrics = provider.getMetrics();
        assertNotNull(metrics);
        assertEquals(75.0f, metrics.getLineCoveragePercent(), 0.0f);
    }

    @Test
    public void testGetLineCoverageNull() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        FreeStyleBuild build = new FreeStyleBuild(project);
        provider.setBuild(build);
        assertNull(provider.readLineCoverage());
    }

    @Test
    public void testGetLineCoverageWithFile() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        FreeStyleBuild build = new FreeStyleBuild(project);

        File coverageFile = new File(build.getRootDir(), "coverage0.xml");
        assertTrue(build.getRootDir().mkdirs());
        InputStream in = getClass().getResourceAsStream("go-torch-coverage.xml");
        OutputStream out = new BufferedOutputStream(new FileOutputStream(coverageFile));
        IOUtils.copy(in, out);
        out.close();
        provider.setBuild(build);

        Map<String, List<Integer>> coverage = provider.readLineCoverage();

        assertNotNull(coverage);
        assertEquals(1, coverage.get("github.com/uber/go-torch/visualization/visualization.go").get(66).longValue());
    }

    @Test
    public void testParseReportsIOException() throws Exception {
        CoberturaXMLParser parser = mock(CoberturaXMLParser.class);
        when(parser.parse(any(File.class))).thenThrow(IOException.class);
        assertNull(provider.parseReports(parser, new File[]{mock(File.class)}));
    }

    @Test
    public void testParseReportsParserException() throws Exception {
        CoberturaXMLParser parser = mock(CoberturaXMLParser.class);
        when(parser.parse(any(File.class))).thenThrow(ParserConfigurationException.class);
        assertNull(provider.parseReports(parser, new File[]{mock(File.class)}));
    }

    @Test
    public void testParseReportsSAXException() throws Exception {
        CoberturaXMLParser parser = mock(CoberturaXMLParser.class);
        when(parser.parse(any(File.class))).thenThrow(SAXException.class);
        assertNull(provider.parseReports(parser, new File[]{mock(File.class)}));
    }

    private CoverageResult getMockResult() {
        Ratio ratio = Ratio.create(75.0f, 100.0f);
        CoverageResult result = mock(CoverageResult.class);
        when(result.getCoverage(CoverageMetric.LINE)).thenReturn(ratio);
        return result;
    }

    private FreeStyleBuild getBuild() throws IOException {
        return new FreeStyleBuild(j.createFreeStyleProject());
    }
}
