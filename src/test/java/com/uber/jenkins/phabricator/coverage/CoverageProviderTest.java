package com.uber.jenkins.phabricator.coverage;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CoverageProviderTest {

    @Test
    public void nullBuildHasNoCoverage() {
        CoverageProvider coverageProvider = new FakeCoverageProvider(Collections.emptyMap());
        assertFalse(coverageProvider.hasCoverage());
        assertNull(coverageProvider.getCoverageMetrics());
    }

    @Test
    public void emptyBuildHasNoCoverage() {
        CoverageProvider coverageProvider = new FakeCoverageProvider(Collections.emptyMap());
        assertFalse(coverageProvider.hasCoverage());
        assertNull(coverageProvider.getCoverageMetrics());
    }

    @Test
    public void noLinesCovered() {
        CoverageProvider coverageProvider = new FakeCoverageProvider(Collections.emptyMap());
        coverageProvider.metrics = new CodeCoverageMetrics(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0, 0);
        assertFalse(coverageProvider.hasCoverage());
    }

    @Test
    public void linesCovered() {
        CoverageProvider coverageProvider = new FakeCoverageProvider(Collections.emptyMap());
        coverageProvider.metrics = new CodeCoverageMetrics(1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1, 1);
        assertTrue(coverageProvider.hasCoverage());
    }

    @Test
    public void relativePaths() {
        Set<String> includeFiles =
                new HashSet<>(Arrays.asList("com/uber/jenkins/phabricator/packageA/Greet.java",
                        "com/uber/jenkins/phabricator/packageB/Greet.java", "eet.java"));

        // exact match
        assertEquals("com/uber/jenkins/phabricator/packageA/Greet.java",
                CoverageProvider.getRelativePathFromProjectRoot(includeFiles,
                        "com/uber/jenkins/phabricator/packageA/Greet.java"));
        // exact match disambiguation
        assertEquals("com/uber/jenkins/phabricator/packageB/Greet.java",
                CoverageProvider.getRelativePathFromProjectRoot(includeFiles,
                        "com/uber/jenkins/phabricator/packageB/Greet.java"));
        // filename match
        assertEquals("com/uber/jenkins/phabricator/packageA/Greet.java",
                CoverageProvider.getRelativePathFromProjectRoot(includeFiles,
                        "Greet.java"));
        // partial match
        assertEquals("kageB/Greet.java", CoverageProvider.getRelativePathFromProjectRoot(includeFiles,
                "kageB/Greet.java"));
    }
}
