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

package com.uber.jenkins.phabricator.coverage;

import hudson.model.AbstractBuild;
import hudson.plugins.cobertura.CoberturaBuildAction;
import hudson.plugins.cobertura.Ratio;
import hudson.plugins.cobertura.targets.CoverageMetric;
import hudson.plugins.cobertura.targets.CoverageResult;

/**
 * Provide Cobertura coverage data
 */
@SuppressWarnings("unused")
public class CoberturaCoverageProvider extends CoverageProvider {
    @Override
    public boolean hasCoverage() {
        CoverageResult result = getCoverageResult();
        if (result == null) {
            return false;
        }
        return result.getCoverage(CoverageMetric.LINE) != null;
    }

    @Override
    public CodeCoverageMetrics getMetrics() {
        CoverageResult result = getCoverageResult();
        if (result == null) {
            return null;
        }
        return convertCobertura(result);
    }

    private CoverageResult getCoverageResult() {
        AbstractBuild build = getBuild();
        if (build == null) {
            return null;
        }

        CoberturaBuildAction coberturaAction = build.getAction(CoberturaBuildAction.class);
        if (coberturaAction == null) {
            return null;
        }
        return coberturaAction.getResult();
    }

    /**
     * Convert Cobertura results to an internal CodeCoverageMetrics representation
     * @param result The cobertura report
     * @return The internal representation of coverage
     */
    public static CodeCoverageMetrics convertCobertura(CoverageResult result) {
        if (result == null) {
            return null;
        }

        float packagesCoverage = getCoveragePercentage(result, CoverageMetric.PACKAGES);
        float filesCoverage = getCoveragePercentage(result, CoverageMetric.FILES);
        float classesCoverage = getCoveragePercentage(result, CoverageMetric.CLASSES);
        float methodCoverage = getCoveragePercentage(result, CoverageMetric.METHOD);
        float lineCoverage = getCoveragePercentage(result, CoverageMetric.LINE);
        float conditionalCoverage = getCoveragePercentage(result, CoverageMetric.CONDITIONAL);

        return new CodeCoverageMetrics(
                packagesCoverage,
                filesCoverage,
                classesCoverage,
                methodCoverage,
                lineCoverage,
                conditionalCoverage
        );
    }

    private static float getCoveragePercentage(CoverageResult result, CoverageMetric metric) {
        Ratio ratio = result.getCoverage(metric);
        if (ratio == null) {
            return 0.0f;
        }
        return ratio.getPercentageFloat();
    }
}
