package com.uber.jenkins.phabricator.coverage;

import hudson.plugins.jacoco.model.Coverage;
import hudson.plugins.jacoco.report.CoverageReport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertEquals;
import static org.powermock.api.mockito.PowerMockito.when;

@PrepareForTest({CoverageReport.class})
@RunWith(PowerMockRunner.class)
public class JacocoCoverageProviderTest {

    @Test
    public void conversion() {
        CoverageReport result = getMockResult();
        CodeCoverageMetrics metrics = JacocoPluginCoverageProvider.convertJacoco(result);
        assertEquals(60.0f, metrics.getMethodCoveragePercent(), 0.1f);
        assertEquals(80.0f, metrics.getClassesCoveragePercent(), 0.1f);
        assertEquals(75.0f, metrics.getLineCoveragePercent(), 0.1f);
        assertEquals(50.0f, metrics.getConditionalCoveragePercent(), 0.1f);
    }

    private CoverageReport getMockResult() {
        CoverageReport report = PowerMockito.mock(CoverageReport.class);
        when(report.getMethodCoverage()).thenReturn(new Coverage(40, 60));
        when(report.getClassCoverage()).thenReturn(new Coverage(20, 80));
        when(report.getLineCoverage()).thenReturn(new Coverage(25, 75));
        when(report.getBranchCoverage()).thenReturn(new Coverage(50, 50));
        when(report.hasLineCoverage()).thenReturn(true);
        return report;
    }
}
