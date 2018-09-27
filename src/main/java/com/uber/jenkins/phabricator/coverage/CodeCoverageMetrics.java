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
    private float packagesCoveragePercent = -1;
    private float filesCoveragePercent = -1;
    private float classesCoveragePercent = -1;
    private float methodCoveragePercent = -1;
    private float lineCoveragePercent = -1;
    private float linesCovered = -1;
    private float linesTested = -1;
    private float conditionalCoveragePercent = -1;

    public CodeCoverageMetrics(float packagesCoveragePercent, float filesCoveragePercent,
                               float classesCoveragePercent, float methodCoveragePercent, float lineCoveragePercent,
                               float linesCovered, float linesTested, float conditionalCoveragePercent) {
        this.packagesCoveragePercent = packagesCoveragePercent;
        this.filesCoveragePercent = filesCoveragePercent;
        this.classesCoveragePercent = classesCoveragePercent;
        this.methodCoveragePercent = methodCoveragePercent;
        this.lineCoveragePercent = lineCoveragePercent;
        this.linesCovered = linesCovered;
        this.linesTested = linesTested;
        this.conditionalCoveragePercent = conditionalCoveragePercent;
    }

    public boolean isValid() {
        return lineCoveragePercent != -1;
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

    public float getLinesCovered() {
        return linesCovered;
    }

    public float getLinesTested() {
        return linesTested;
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
        sb.append(", lines covered = ");
        sb.append(linesCovered);
        sb.append(", lines tested = ");
        sb.append(linesTested);
        sb.append(", conditional coverage = ");
        sb.append(conditionalCoveragePercent);
        return sb.toString();
    }
}
