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

import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class CoverageProvider {
    private AbstractBuild<?, ?> build;
    private Set<String> includeFileNames;

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
    public void setBuild(AbstractBuild<?, ?> build) {
        this.build = build;
    }

    protected AbstractBuild getBuild() {
        return build;
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
