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

package com.uber.jenkins.phabricator;

import com.uber.jenkins.phabricator.utils.CommonUtils;
import com.uber.jenkins.phabricator.utils.Logger;

import org.apache.commons.io.IOUtils;

import java.io.IOException;

import hudson.FilePath;

import static java.lang.Integer.parseInt;

public class RemoteFileFetcher {

    private static final int DEFAULT_MAX_SIZE = 1000;
    private static final String LOGGER_TAG = "file-fetcher";

    private final FilePath workspace;
    private final Logger logger;
    private final String fileName;
    private final String maxSize;

    public RemoteFileFetcher(FilePath workspace, Logger logger, String fileName, String maxSize) {
        this.workspace = workspace;
        this.logger = logger;
        this.fileName = fileName;
        this.maxSize = maxSize;
    }

    /**
     * Attempt to read a remote  file
     *
     * @return the content of the remote comment file, if present
     * @throws InterruptedException if there is an error fetching the file
     * @throws IOException if any network error occurs
     */
    public String getRemoteFile() throws InterruptedException, IOException {
        if (CommonUtils.isBlank(fileName)) {
            logger.info(LOGGER_TAG, "no file configured");
            return null;
        }

        FilePath[] src = workspace.list(fileName);
        if (src.length == 0) {
            logger.info(LOGGER_TAG, "no files found by path: '" + fileName + "'");
            return null;
        }
        if (src.length > 1) {
            logger.info(LOGGER_TAG, "Found multiple matches. Reading first only.");
        }

        FilePath source = src[0];

        int maxLength = DEFAULT_MAX_SIZE;
        if (!CommonUtils.isBlank(maxSize)) {
            maxLength = parseInt(maxSize, 10);
        }
        if (source.length() < maxLength) {
            maxLength = (int) source.length();
        }
        byte[] buffer = new byte[maxLength];
        IOUtils.read(source.read(), buffer);

        return new String(buffer);
    }
}
