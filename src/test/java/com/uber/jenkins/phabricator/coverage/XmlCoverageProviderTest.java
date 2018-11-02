package com.uber.jenkins.phabricator.coverage;

import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class XmlCoverageProviderTest {

    private static final String TEST_COVERAGE_FILE = "go-torch-coverage.xml";
    private static final String TEST_COVERAGE_FILE_1 = "go-torch-coverage1.xml";
    private static final String TEST_COVERAGE_FILE_2 = "go-torch-coverage2.xml";
    private static final String TEST_COVERAGE_FILE_3 = "go-torch-coverage3.xml";
    private static final String TEST_COVERAGE_FILE_MULTIPLE_INCLUDE = "multiple-include-coverage.xml";
    private static final String TEST_COVERAGE_FILE_INVALID = "invalid-coverage.xml";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void emptyCoverage() {
        CoverageProvider provider = new XmlCoverageProvider(Collections.emptySet());
        assertFalse(provider.hasCoverage());
    }

    @Test
    public void cobertura() {
        CoverageProvider provider = new XmlCoverageProvider(getResources(TEST_COVERAGE_FILE));
        assertTrue(provider.hasCoverage());

        Map<String, List<Integer>> coverage = provider.getLineCoverage();
        assertEquals(1, coverage.get("github.com/uber/go-torch/visualization/visualization.go").get(66).longValue());
        assertEquals(new CodeCoverageMetrics(100.0f, 100.0f, 86.666664f, 83.33333f, 89.69697f, 100.0f, 148, 165),
                provider.getMetrics());
    }

    @Test
    public void jacoco() {
        CoverageProvider provider = new XmlCoverageProvider(getResources("jacoco-coverage.xml"));

        assertTrue(provider.hasCoverage());
        Map<String, List<Integer>> coverage = provider.getLineCoverage();
        assertEquals(1, coverage.get("com/uber/nullaway/jarinfer/StubxWriter.java").get(72).longValue());
        assertEquals(0, coverage.get("com/uber/nullaway/jarinfer/StubxWriter.java").get(73).longValue());
        assertEquals(new CodeCoverageMetrics(100.0f, 100.0f, 100.f, 92.59259f, 90.10989f, 69.09091f, 328, 364),
                provider.getMetrics());
    }

    @Test
    public void lineCoverageAggregation() {
        CoverageProvider provider = new XmlCoverageProvider(getResources(
                TEST_COVERAGE_FILE_1,
                TEST_COVERAGE_FILE_2,
                TEST_COVERAGE_FILE_3));

        Map<String, List<Integer>> lineCoverage = provider.getLineCoverage();
        List<Integer> mainCoverage = lineCoverage.get("github.com/uber/go-torch/main.go");
        assertEquals(246, mainCoverage.size());
        assertNull(mainCoverage.get(0));
        assertNull(mainCoverage.get(1));
        assertEquals(1, mainCoverage.get(78).longValue());
        assertNull(mainCoverage.get(79));
        assertEquals(1, mainCoverage.get(85).longValue());
        assertEquals(0, mainCoverage.get(102).longValue());

        List<Integer> graphCoverage = lineCoverage.get("github.com/uber/go-torch/graph/graph.go");
        assertEquals(1, graphCoverage.get(234).longValue());
        assertNull(graphCoverage.get(235));
    }

    @Test
    public void lineCoverageWithIncludes() {
        CoverageProvider provider = new XmlCoverageProvider(getResources(TEST_COVERAGE_FILE),
                Collections.singleton("github.com/uber/go-torch/main.go"));

        Map<String, List<Integer>> lineCoverage = provider.getLineCoverage();
        List<Integer> mainCoverage = lineCoverage.get("github.com/uber/go-torch/main.go");
        assertEquals(1, mainCoverage.get(212).longValue());

        List<Integer> graphCoverage = lineCoverage.get("github.com/uber/go-torch/graph.go");
        assertNull(graphCoverage);
    }

    @Test
    public void lineCoverageWithMultipleIncludes() {
        CoverageProvider provider = new XmlCoverageProvider(getResources(TEST_COVERAGE_FILE_MULTIPLE_INCLUDE),
                new HashSet<>(Arrays.asList("com/uber/jenkins/phabricator/packageA/Greet.java", "com/uber/jenkins"
                        + "/phabricator/packageB/Greet.java", "eet.java", "kageB/Greet.java")));

        Map<String, List<Integer>> lineCoverage = provider.getLineCoverage();
        List<Integer> greetACoverage = lineCoverage.get("com/uber/jenkins/phabricator/packageA/Greet.java");
        List<Integer> greetBCoverage = lineCoverage.get("com/uber/jenkins/phabricator/packageB/Greet.java");
        List<Integer> eetCoverage = lineCoverage.get("eet.java");
        List<Integer> partialMatchCoverage = lineCoverage.get("kageB/Greet.java");
        assertEquals(0, greetACoverage.get(6).longValue());
        assertEquals(1, greetBCoverage.get(6).longValue());
        assertNull(eetCoverage);
        assertNull(partialMatchCoverage);
    }

    @Test(expected = IllegalStateException.class)
    public void invalidCoverage() {
        CoverageProvider provider = new XmlCoverageProvider(getResources(TEST_COVERAGE_FILE_INVALID));
        provider.getLineCoverage();
    }

    private Set<File> getResources(String... resources) {
        Set<File> copiedFiles = new HashSet<>();
        for (String resource : resources) {
            try {
                File copiedFile = tmp.newFile(resource);
                try (InputStream in = getClass().getResourceAsStream(resource);
                        OutputStream out = new BufferedOutputStream(new FileOutputStream(copiedFile))) {
                    IOUtils.copy(in, out);
                    copiedFiles.add(copiedFile);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return copiedFiles;
    }
}
