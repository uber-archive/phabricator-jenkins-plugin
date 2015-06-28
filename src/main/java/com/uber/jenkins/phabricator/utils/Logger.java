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

package com.uber.jenkins.phabricator.utils;

import java.io.PrintStream;

/**
 * Logger utility.
 */
public class Logger {
    private static final String LOG_FORMAT = "[%s] %s";

    private PrintStream stream;

    /**
     * Logger constructor.
     * @param stream The stream.
     */
    public Logger(PrintStream stream) {
        this.stream = stream;
    }

    /**
     * Gets the stream.
     * @return The stream where logs go to.
     */
    public PrintStream getStream() {
        return stream;
    }

    /**
     * Logs the message to the stream.
     * @param tag The tag for the message.
     * @param message The message to log.
     */
    public void info(String tag, String message) {
        stream.println(String.format(LOG_FORMAT, tag, message));
    }
}
