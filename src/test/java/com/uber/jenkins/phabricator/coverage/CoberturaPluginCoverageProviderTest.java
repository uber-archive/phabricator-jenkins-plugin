package com.uber.jenkins.phabricator.coverage;

import hudson.plugins.cobertura.Ratio;
import hudson.plugins.cobertura.targets.CoverageMetric;
import hudson.plugins.cobertura.targets.CoverageResult;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

public class CoberturaPluginCoverageProviderTest {

    @Test
    public void conversion() {
        CoverageResult result = getMockResult();
        CodeCoverageMetrics metrics = CoberturaPluginCoverageProvider.convertCobertura(result);
        assertEquals(75.0f, metrics.getLineCoveragePercent(), 0.0f);
        assertEquals(0.0f, metrics.getPackageCoveragePercent(), 0.0f);
    }

    private CoverageResult getMockResult() {
        Ratio ratio = Ratio.create(75.0f, 100.0f);
        CoverageResult result = mock(CoverageResult.class);
        when(result.getCoverage(CoverageMetric.LINE)).thenReturn(ratio);
        return result;
    }
}
