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

package com.uber.jenkins.phabricator.coverage;

public class CodeCoverageMetrics {

    private final float packagesCoveragePercent;
    private final float filesCoveragePercent;
    private final float classesCoveragePercent;
    private final float methodCoveragePercent;
    private final float lineCoveragePercent;
    private final float conditionalCoveragePercent;
    private final long linesCovered;
    private final long linesTested;

    public CodeCoverageMetrics(
            float packagesCoveragePercent, float filesCoveragePercent,
            float classesCoveragePercent, float methodCoveragePercent, float lineCoveragePercent,
            float conditionalCoveragePercent, long linesCovered, long linesTested) {
        this.packagesCoveragePercent = packagesCoveragePercent;
        this.filesCoveragePercent = filesCoveragePercent;
        this.classesCoveragePercent = classesCoveragePercent;
        this.methodCoveragePercent = methodCoveragePercent;
        this.lineCoveragePercent = lineCoveragePercent;
        this.conditionalCoveragePercent = conditionalCoveragePercent;
        this.linesCovered = linesCovered;
        this.linesTested = linesTested;
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

    public float getLinesCovered() {
        return linesCovered;
    }

    public float getLinesTested() {
        return linesTested;
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
        sb.append(", lines covered = ");
        sb.append(linesCovered);
        sb.append(", linesTested = ");
        sb.append(linesTested);
        return sb.toString();
    }
}
