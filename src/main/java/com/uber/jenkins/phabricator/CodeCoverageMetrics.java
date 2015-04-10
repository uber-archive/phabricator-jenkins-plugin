// Copyright (c) 2015 Uber Technologies, Inc.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

package com.uber.jenkins.phabricator;

import hudson.plugins.cobertura.Ratio;
import hudson.plugins.cobertura.targets.CoverageMetric;
import hudson.plugins.cobertura.targets.CoverageResult;

public class CodeCoverageMetrics {
    private String sha1;

    private float packagesCoveragePercent = -1;
    private float filesCoveragePercent = -1;
    private float classesCoveragePercent = -1;
    private float methodCoveragePercent = -1;
    private float lineCoveragePercent = -1;
    private float conditionalCoveragePercent = -1;

    public CodeCoverageMetrics(CoverageResult coverageResult) {
        if (coverageResult != null) {
            Ratio packagesCoverage = coverageResult.getCoverage(CoverageMetric.PACKAGES);
            if (packagesCoverage != null) {
                packagesCoveragePercent = packagesCoverage.getPercentageFloat();
            }

            Ratio filesCoverage = coverageResult.getCoverage(CoverageMetric.FILES);
            if (filesCoverage != null) {
                filesCoveragePercent = filesCoverage.getPercentageFloat();
            }

            Ratio classesCoverage = coverageResult.getCoverage(CoverageMetric.CLASSES);
            if (classesCoverage != null) {
                classesCoveragePercent = classesCoverage.getPercentageFloat();
            }

            Ratio methodCoverage = coverageResult.getCoverage(CoverageMetric.METHOD);
            if (methodCoverage != null) {
                methodCoveragePercent = methodCoverage.getPercentageFloat();
            }

            Ratio lineCoverage = coverageResult.getCoverage(CoverageMetric.LINE);
            if (lineCoverage != null) {
                lineCoveragePercent = lineCoverage.getPercentageFloat();
            }

            Ratio conditionalCoverage = coverageResult.getCoverage(CoverageMetric.CONDITIONAL);
            if (conditionalCoverage != null) {
                conditionalCoveragePercent = conditionalCoverage.getPercentageFloat();
            }
        }
    }

    public CodeCoverageMetrics(String sha1, float packagesCoveragePercent, float filesCoveragePercent,
                               float classesCoveragePercent, float methodCoveragePercent, float lineCoveragePercent,
                               float conditionalCoveragePercent) {
        this.sha1 = sha1;
        this.packagesCoveragePercent = packagesCoveragePercent;
        this.filesCoveragePercent = filesCoveragePercent;
        this.classesCoveragePercent = classesCoveragePercent;
        this.methodCoveragePercent = methodCoveragePercent;
        this.lineCoveragePercent = lineCoveragePercent;
        this.conditionalCoveragePercent = conditionalCoveragePercent;
    }

    public CodeCoverageMetrics(float packages, float files, float classes, float method, float line, float conditional) {
        this(null, packages, files, classes, method, line, conditional);
    }

    public boolean isValid() {
        return lineCoveragePercent != -1;
    }

    public String getSha1() {
        return sha1;
    }

    public float getPackageCoveragePercent() {
        return packagesCoveragePercent;
    }

    public float getFilesCoveragePercent() {
        return filesCoveragePercent;
    }

    public float getClassesCoveragePercent() {
        return classesCoveragePercent;
    }

    public float getMethodCoveragePercent() {
        return methodCoveragePercent;
    }

    public float getLineCoveragePercent() {
        return lineCoveragePercent;
    }

    public float getConditionalCoveragePercent() {
        return conditionalCoveragePercent;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("package coverage = ");
        sb.append(packagesCoveragePercent);
        sb.append(", files coverage = ");
        sb.append(filesCoveragePercent);
        sb.append(", classes coverage = ");
        sb.append(classesCoveragePercent);
        sb.append(", method coverage = ");
        sb.append(methodCoveragePercent);
        sb.append(", line coverage = ");
        sb.append(lineCoveragePercent);
        sb.append(", conditional coverage = ");
        sb.append(conditionalCoveragePercent);
        return sb.toString();
    }
}
