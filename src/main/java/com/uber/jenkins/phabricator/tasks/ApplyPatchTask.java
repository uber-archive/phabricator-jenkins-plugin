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

package com.uber.jenkins.phabricator.tasks;

import com.uber.jenkins.phabricator.LauncherFactory;
import com.uber.jenkins.phabricator.conduit.ArcanistClient;
import com.uber.jenkins.phabricator.utils.Logger;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ApplyPatchTask extends Task {
    private final LauncherFactory starter;
    private final String baseCommit;
    private final String diffID;
    private final PrintStream logStream;
    private final String conduitToken;
    private final String arcPath;
    private final boolean createCommit;
    private final String gitPath;
    private final boolean skipForcedClean;
    private final boolean createBranch;
    private final boolean patchWithForceFlag;
    private final String scmType;

    public ApplyPatchTask(Logger logger, LauncherFactory starter, String baseCommit,
                          String diffID, String conduitToken, String arcPath,
                          String gitPath, boolean createCommit, boolean skipForcedClean,
                          boolean createBranch, boolean patchWithForceFlag,
                          String scmType) {
        super(logger);
        this.starter = starter;
        this.baseCommit = baseCommit;
        this.diffID = diffID;
        this.conduitToken = conduitToken;
        this.arcPath = arcPath;
        this.gitPath = gitPath;
        this.createCommit = createCommit;
        this.skipForcedClean = skipForcedClean;
        this.createBranch = createBranch;
        this.patchWithForceFlag = patchWithForceFlag;
        this.scmType = scmType;

        this.logStream = logger.getStream();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getTag() {
        return "arc-patch";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setup() {
        // Do nothing
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void execute() {
        try {
            int exitCode;
            if (this.scmType.equals("git")) {
                exitCode = starter.launch()
                        .cmds(Arrays.asList(gitPath, "reset", "--hard", baseCommit))
                        .stdout(logStream)
                        .join();

                if (exitCode != 0) {
                    info("Got non-zero exit code resetting to base commit " + baseCommit + ": " + exitCode);
                }

                if (!skipForcedClean) {
                    // Clean workspace, otherwise `arc patch` may fail
                    starter.launch()
                        .stdout(logStream)
                        .cmds(Arrays.asList(gitPath, "clean", "-fd", "-f"))
                        .join();
                }

                // Update submodules recursively.
                starter.launch()
                        .stdout(logStream)
                        .cmds(Arrays.asList(gitPath, "submodule", "update", "--init", "--recursive"))
                        .join();
            }

            List<String> arcPatchParams = new ArrayList<String>(Arrays.asList("--diff", diffID));

            if (this.scmType.equals("git")) {
                if (!createCommit) {
                    arcPatchParams.add("--nocommit");
                }

                if (!createBranch) {
                    arcPatchParams.add("--nobranch");
                }
            }

            if (patchWithForceFlag) {
                arcPatchParams.add("--force");
            }

            ArcanistClient arc = new ArcanistClient(
                    arcPath,
                    "patch",
                    conduitToken,
                    arcPatchParams.toArray(new String[arcPatchParams.size()]));

            exitCode = arc.callConduit(starter.launch(), logStream);
            this.result = exitCode == 0 ? Result.SUCCESS : Result.FAILURE;
        } catch (IOException e) {
            e.printStackTrace(logStream);
            this.result = Result.FAILURE;
        } catch (InterruptedException e) {
            e.printStackTrace(logStream);
            this.result = Result.FAILURE;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void tearDown() {
        // Do nothing
    }
}
