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

import hudson.FilePath;
import hudson.model.Run;

import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class CoverageProvider {
    private Run<?, ?> build;
    private FilePath workspace;
    private Set<String> includeFileNames;
    private String coverageReportPattern;

    /**
     * Set the list of file names to get coverage metrics for
     * @param includeFileNames The list of file names to get coverage metrics for
     */
    public void setIncludeFileNames(Set<String> includeFileNames) {
        this.includeFileNames = includeFileNames;
    }

    protected Set<String> getIncludeFileNames() {
        return includeFileNames;
    }

    /**
     * Set the owning build for this provider
     * @param build The build that is associated with the current run
     */
    public void setBuild(Run<?, ?> build) {
        this.build = build;
    }

    protected Run<?, ?> getBuild() {
        return build;
    }

    public void setWorkspace(FilePath workspace) {
        this.workspace = workspace;
    }

    protected FilePath getWorkspace() {
        return workspace;
    }

    /**
     * Set the coverage report pattern to scan for
     * @param coverageReportPattern The coverage report pattern to scan for
     */
    public void setCoverageReportPattern(String coverageReportPattern) {
        this.coverageReportPattern = coverageReportPattern;
    }

    String getCoverageReportPattern() {
        return coverageReportPattern;
    }

    public abstract Map<String, List<Integer>> readLineCoverage();

    public abstract boolean hasCoverage();

    /**
     * Get the coverage metrics for the provider
     * @return The metrics, if any are available
     */
    public CodeCoverageMetrics getMetrics() {
        if (!hasCoverage()) {
            return null;
        }
        return getCoverageMetrics();
    }

    protected abstract CodeCoverageMetrics getCoverageMetrics();
}
