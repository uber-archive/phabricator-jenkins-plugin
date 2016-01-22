// Copyright (c) 2016 Uber
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

import java.io.IOException;
import java.util.List;

public class PathResolver {
    private final FilePath root;
    private final List<String> candidates;

    public PathResolver(FilePath root, List<String> candidates) {
        this.root = root;
        this.candidates = candidates;
    }

    /**
     * Using the workspace's root FilePath and a file that is presumed to exist on the node running the tests,
     * recurse over the `sources` provided by Cobertura and look for a combination where the file exists.
     *
     * This is a heuristic, and not perfect, to overcome changes to Python's coverage.py module which introduced
     * additional `source` directories in version 4.0.3
     */
    public String choose(String filename) {
        for (String sourceDir : candidates) {
            FilePath candidate = new FilePath(root, sourceDir);
            candidate = new FilePath(candidate, filename);
            try {
                if (candidate.exists()) {
                    return sourceDir;
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return null;
    }
}
