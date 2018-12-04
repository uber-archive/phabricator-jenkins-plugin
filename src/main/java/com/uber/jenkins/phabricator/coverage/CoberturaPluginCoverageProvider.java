// Copyright (c) 2018 Uber
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

package com.uber.jenkins.phabricator.coverage;

import java.io.File;
import java.util.Set;

import com.google.common.annotations.VisibleForTesting;
import hudson.plugins.cobertura.CoberturaBuildAction;
import hudson.plugins.cobertura.Ratio;
import hudson.plugins.cobertura.targets.CoverageMetric;
import hudson.plugins.cobertura.targets.CoverageResult;

/**
 * Provide Cobertura coverage data via the Jenkins Cobertura Plugin
 */
public class CoberturaPluginCoverageProvider extends XmlCoverageProvider {

    private final CoberturaBuildAction buildAction;

    public CoberturaPluginCoverageProvider(Set<File> coverageReports, Set<String> includeFiles, CoberturaBuildAction buildAction) {
        super(coverageReports, includeFiles);
        this.buildAction = buildAction;
    }

    @Override
    protected void computeMetrics() {
        CoverageResult coverageResult = buildAction.getResult();
        metrics = convertCobertura(coverageResult);
    }

    /**
     * Convert Cobertura results to an internal CodeCoverageMetrics representation
     *
     * @param result The cobertura report
     * @return The internal representation of coverage
     */
    @VisibleForTesting
     static CodeCoverageMetrics convertCobertura(CoverageResult result) {
        if (result == null) {
            return null;
        }

        float packagesCoverage = getCoveragePercentage(result, CoverageMetric.PACKAGES);
        float filesCoverage = getCoveragePercentage(result, CoverageMetric.FILES);
        float classesCoverage = getCoveragePercentage(result, CoverageMetric.CLASSES);
        float methodCoverage = getCoveragePercentage(result, CoverageMetric.METHOD);
        float lineCoverage = getCoveragePercentage(result, CoverageMetric.LINE);
        float conditionalCoverage = getCoveragePercentage(result, CoverageMetric.CONDITIONAL);
        long linesCovered = (long) getCoverageRatio(result, CoverageMetric.LINE).numerator;
        long linesTested = (long) getCoverageRatio(result, CoverageMetric.LINE).denominator;

        return new CodeCoverageMetrics(
                packagesCoverage,
                filesCoverage,
                classesCoverage,
                methodCoverage,
                lineCoverage,
                conditionalCoverage,
                linesCovered,
                linesTested
        );
    }

    private static Ratio getCoverageRatio(CoverageResult result, CoverageMetric metric) {
        Ratio ratio = result.getCoverage(metric);
        if (ratio == null) {
            return Ratio.create(0,0);
        }
        return ratio;
    }

    private static float getCoveragePercentage(CoverageResult result, CoverageMetric metric) {
        Ratio ratio = result.getCoverage(metric);
        if (ratio == null) {
            return 0.0f;
        }
        return ratio.getPercentageFloat();
    }
}
