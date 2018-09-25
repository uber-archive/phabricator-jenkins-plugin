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

import org.apache.commons.io.FilenameUtils;

import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Run;

public abstract class CoverageProvider {

    private static final String DEFAULT_COVERAGE_REPORT_PATTERN = "**/coverage*.xml, **/cobertura*.xml";

    final Run<?, ?> build;
    final FilePath workspace;
    final Map<String, String> includeFiles;
    final String coverageReportPattern;

    CoverageProvider(Run<?, ?> build, Map<String, String> includeFiles, String coverageReportPattern) {
        this.build = build;
        this.workspace = build != null ? ((AbstractBuild) build).getWorkspace() : null;
        this.includeFiles = includeFiles;
        this.coverageReportPattern =
                coverageReportPattern != null ? coverageReportPattern : DEFAULT_COVERAGE_REPORT_PATTERN;
    }

    public abstract Map<String, List<Integer>> readLineCoverage();

    public abstract boolean hasCoverage();

    /**
     * Get the coverage metrics for the provider
     * @return The metrics, if any are available
     */
    @Nullable
    public CodeCoverageMetrics getMetrics() {
        if (!hasCoverage()) {
            return null;
        }
        return getCoverageMetrics();
    }

    @Nullable
    String getRelativePathFromProjectRoot(String file) {
        return getRelativePathFromProjectRoot(includeFiles, file);
    }

    @Nullable
    static String getRelativePathFromProjectRoot(Map<String, String> includeFiles, String file) {
        String finalFile = null;
        if (includeFiles == null || includeFiles.isEmpty()) {
            finalFile = file;
        } else {
            String includedFileName = FilenameUtils.getName(file);
            String foundFile = includeFiles.get(includedFileName);
            if (foundFile != null) {
                finalFile = foundFile;
            }
        }
        return finalFile;
    }

    protected abstract CodeCoverageMetrics getCoverageMetrics();
}
