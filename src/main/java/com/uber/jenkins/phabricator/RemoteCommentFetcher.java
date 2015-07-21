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
import hudson.FilePath;

import java.io.IOException;

import static java.lang.Integer.parseInt;

public class RemoteCommentFetcher {
    private static final int DEFAULT_COMMENT_SIZE = 1000;
    private static final String LOGGER_TAG = "comment-file";

    private final FilePath workspace;
    private final Logger logger;
    private final String commentFile;
    private final String maxSize;

    public RemoteCommentFetcher(FilePath workspace, Logger logger, String commentFile, String maxSize) {
        this.workspace = workspace;
        this.logger = logger;
        this.commentFile = commentFile;
        this.maxSize = maxSize;
    }

    /**
     * Attempt to read a remote comment file
     * @return the contents of the string
     * @throws InterruptedException
     */
    public String getRemoteComment() throws InterruptedException, IOException {
        if (CommonUtils.isBlank(commentFile)) {
            logger.info(LOGGER_TAG, "no comment file configured");
            return null;
        }

        FilePath[] src = workspace.list(commentFile);
        if (src.length == 0) {
            logger.info(LOGGER_TAG, "no files found by path: '" + commentFile + "'");
            return null;
        }
        if (src.length > 1) {
            logger.info(LOGGER_TAG, "Found multiple matches. Reading first only.");
        }

        FilePath source = src[0];

        int maxLength = DEFAULT_COMMENT_SIZE;
        if (!CommonUtils.isBlank(maxSize)) {
            maxLength = parseInt(maxSize, 10);
        }
        if (source.length() < maxLength) {
            maxLength = (int)source.length();
        }
        byte[] buffer = new byte[maxLength];
        source.read().read(buffer, 0, maxLength);
        return new String(buffer);
    }
}
