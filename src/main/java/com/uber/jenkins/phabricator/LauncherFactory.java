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

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;

import java.io.PrintStream;

public class LauncherFactory {
    private final Launcher launcher;
    private final PrintStream stderr;
    private final EnvVars environment;
    private final FilePath pwd;

    public LauncherFactory(Launcher launcher, EnvVars environment, PrintStream stderr, FilePath pwd) {
        this.launcher = launcher;
        this.environment = environment;
        this.stderr = stderr;
        this.pwd = pwd;
    }

    public PrintStream getStderr() {
        return this.stderr;
    }

    /**
     * Create a launcher
     * @return a launcher suitable for executing programs within Jenkins
     */
    public Launcher.ProcStarter launch() {
        return launcher.launch().envs(environment).stderr(stderr).pwd(pwd);
    }
}
