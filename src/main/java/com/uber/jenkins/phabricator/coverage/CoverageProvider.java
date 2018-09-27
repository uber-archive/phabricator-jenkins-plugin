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
import java.util.Set;

import javax.annotation.Nullable;

import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Run;

public abstract class CoverageProvider {

    private static final String DEFAULT_COVERAGE_REPORT_PATTERN = "**/coverage*.xml, **/cobertura*.xml";

    final Run<?, ?> build;
    final FilePath workspace;
    final Set<String> includeFiles;
    final String coverageReportPattern;

    CoverageProvider(Run<?, ?> build, Set<String> includeFiles, String coverageReportPattern) {
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
     *
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

    /**
     * Languages like Kotlin/Scala which can have multiple top level classes in a file. For such classes,
     * (package path + source file name) will not match where they are actually present in the filesystem.
     * So this method does two separate marches 1) with just file name and 2) with package name + sourcefile name.
     * Multiple classes with same name and different packages will match correctly. In case a full match is not
     * possible, we fall back to file name match. We only check against files included as part of a diff which means
     * that the possibility of a bad match is very unlikely (only if two files with same name are touched as part of
     * the diff), but that is the best we can accomplish.
     */
    @Nullable
    static String getRelativePathFromProjectRoot(Set<String> includeFiles, String coverageFile) {
        if (includeFiles == null || includeFiles.isEmpty()) {
            return coverageFile;
        } else {
            int maxMatch = 0;
            String maxMatchFile = coverageFile;
            for (String includedFile : includeFiles) {
                int currmatch = suffixMatch(includedFile, coverageFile);
                if (currmatch > maxMatch) {
                    maxMatch = currmatch;
                    maxMatchFile = includedFile;
                }
            }

            // Make sure the match is atleast a full match on the filename and not an accidental partial match
            if (FilenameUtils.getName(maxMatchFile).equals(FilenameUtils.getName(coverageFile))) {
                return maxMatchFile;
            } else {
                return coverageFile;
            }
        }
    }

    private static int suffixMatch(String changedFile, String coverageFile) {
        int changedFileSize = changedFile.length();
        int coverageFileSize = coverageFile.length();

        if (coverageFileSize > changedFileSize) {
            return 0;
        }

        int rIndex = 1;
        while (coverageFileSize - rIndex >= 0 && coverageFile.charAt(coverageFileSize - rIndex) == changedFile.charAt(
                changedFileSize - rIndex)) {
            rIndex++;
        }
        return rIndex - 1;
    }

    protected abstract CodeCoverageMetrics getCoverageMetrics();
}
