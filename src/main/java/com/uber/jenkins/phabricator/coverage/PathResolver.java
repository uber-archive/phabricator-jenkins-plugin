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
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;

import java.io.IOException;
import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PathResolver {
    private final FilePath root;
    private final List<String> candidates;

    public PathResolver(FilePath root, List<String> candidates) {
        this.root = root;
        this.candidates = candidates;
    }

    /**
     * Using the workspace's root FilePath and files that is presumed to exist on the node running the tests,
     * recurse over the `sources` provided by Cobertura and look for a combination where the file exists.
     *
     * This is a heuristic, and not perfect, to overcome changes to Python's coverage.py module which introduced
     * additional `source` directories in version 4.0.3
     *
     * Returns map where key is filename and value - sourceDirectory
     */

    public Map<String, String> choose(List<String> filenames) {
        try {
            if (candidates.size() > 0) {
                return root.act(new PathResolverChooseMultiCallable(candidates, filenames));
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return Collections.emptyMap();
    }

    private static final class PathResolverChooseMultiCallable extends MasterToSlaveFileCallable<Map<String, String>> {
        private final List<String> candidates;
        private final List<String> filenames;

        private PathResolverChooseMultiCallable(List<String> candidates, List<String> filenames) {
            this.candidates = candidates;
            this.filenames = filenames;
        }

        public Map<String, String> invoke(File f, VirtualChannel channel) {
            Map<String, String> res = new HashMap<String, String>(filenames.size());
            for (String filename : filenames) {
                for (String sourceDir : candidates) {
                    File candidate = new File(f, sourceDir);
                    candidate = new File(candidate, filename);
                    if (candidate.exists()) {
                        res.put(filename, sourceDir);
                    }
                }
            }
            return res;
        }
    }
}
