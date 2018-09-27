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

/**
 * Convert {filename: int[] hitCount} data into the Harbormaster format
 *
 * This is a string, where each character represents a line. The code is as follows:
 *
 * 'N': not executable
 * 'C': covered
 * 'U': uncovered
 *
 * For example a hit count of {null, 2, 0, 1} we would get "NCUC"
 */
public final class CoverageConverter {

    private CoverageConverter() {}

    /**
     * Convert line coverage to the Harbormaster coverage format
     * @return The Harbormaster-formatted coverage
     */
    public static Map<String, String> convert(Map<String, List<Integer>> lineCoverage) {
        Map<String, String> results = new HashMap<String, String>();
        for (Map.Entry<String, List<Integer>> entry : lineCoverage.entrySet()) {
            results.put(entry.getKey(), convertFileCoverage(entry.getValue()));
        }

        return results;
    }

    private static String convertFileCoverage(List<Integer> lineCoverage) {
        StringBuilder sb = new StringBuilder();
        for (Integer line : lineCoverage) {
            // Can't use a case statement because NULL
            if (line == null) {
                sb.append('N');
            } else if (line == 0) {
                sb.append('U');
            } else {
                sb.append('C');
            }
        }
        return sb.toString();
    }
}
