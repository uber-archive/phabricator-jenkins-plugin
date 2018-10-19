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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

public abstract class CoverageProvider {

    final Set<String> includeFiles;
    final Map<String, List<Integer>> lineCoverage = new HashMap<>();
    CodeCoverageMetrics metrics = null;

    private boolean hasComputedCoverage = false;

    CoverageProvider(Set<String> includeFiles) {
        this.includeFiles = includeFiles;
    }

    public Map<String, List<Integer>> getLineCoverage() {
        computeCoverageIfNeeded();
        return lineCoverage;
    }

    public boolean hasCoverage() {
        computeCoverageIfNeeded();
        return metrics != null && metrics.getLinesCovered() > 0;
    }

    CodeCoverageMetrics getCoverageMetrics() {
        computeCoverageIfNeeded();
        return metrics;
    }

    public void computeCoverageIfNeeded() {
        if (!hasComputedCoverage) {
            computeCoverage();
            hasComputedCoverage = true;
        }
    }

    /**
     * Use this method to compute and set the coverage metrics and line coverage
     */
    protected abstract void computeCoverage();

    /**
     * Languages like Kotlin/Scala which can have multiple top level classes in a file. For such classes,
     * (package path + source file name) will not match where they are actually present in the filesystem.
     * So this method does two separate matches 1) with just file name and 2) with package name + sourcefile name.
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
            String maxMatchFile = null;
            for (String includedFile : includeFiles) {
                int currmatch = suffixMatch(includedFile, coverageFile);
                if (currmatch > maxMatch) {
                    maxMatch = currmatch;
                    maxMatchFile = includedFile;
                }
            }

            return maxMatchFile;
        }
    }

    private static int suffixMatch(String changedFile, String coverageFile) {
        int changedFileSize = changedFile.length();
        int coverageFileSize = coverageFile.length();

        if (coverageFileSize > changedFileSize) {
            return 0;
        }

        int rIndex = 1;
        while (coverageFileSize > rIndex && coverageFile.charAt(coverageFileSize - rIndex) == changedFile.charAt(
                changedFileSize - rIndex)) {
            rIndex++;
        }

        // Full path match
        if (changedFileSize == rIndex) {
            return rIndex;
        }

        // Make sure the match is not an accidental partial match
        if (changedFileSize > rIndex && changedFile.charAt(changedFileSize - rIndex - 1) != '/') {
            return 0;
        }

        return rIndex;
    }

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
}
