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

import com.google.common.annotations.VisibleForTesting;
import hudson.plugins.jacoco.JacocoBuildAction;
import hudson.plugins.jacoco.report.CoverageReport;

import java.io.File;
import java.util.Set;

/**
 * Provide Jacoco coverage data via the Jenkins Jacoco Plugin
 */
public class JacocoPluginCoverageProvider extends XmlCoverageProvider {

    private final JacocoBuildAction buildAction;

    public JacocoPluginCoverageProvider(Set<File> coverageReports, Set<String> includeFiles, JacocoBuildAction buildAction) {
        super(coverageReports, includeFiles);
        this.buildAction = buildAction;
    }

    @Override
    protected void computeMetrics() {
        CoverageReport coverageResult = buildAction.getResult();
        metrics = convertJacoco(coverageResult);
    }

    /**
     * Convert Jacoco results to an internal CodeCoverageMetrics representation
     *
     * @param coverageResult The jacoco report
     * @return The internal representation of coverage
     */
    @VisibleForTesting
    static CodeCoverageMetrics convertJacoco(CoverageReport coverageResult) {
        if (coverageResult == null) {
            return null;
        }
        float methodCoverage = coverageResult.getMethodCoverage().getPercentageFloat();
        float classCoverage = coverageResult.getClassCoverage().getPercentageFloat();
        float lineCoverage = coverageResult.getLineCoverage().getPercentageFloat();
        float branchCoverage = coverageResult.getBranchCoverage().getPercentageFloat();
        long linesCovered = coverageResult.getLineCoverage().getCovered();
        long linesTested = coverageResult.getLineCoverage().getTotal();
        return new CodeCoverageMetrics(
                -1,
                -1,
                classCoverage,
                methodCoverage,
                lineCoverage,
                branchCoverage,
                linesCovered,
                linesTested
        );
    }
}
