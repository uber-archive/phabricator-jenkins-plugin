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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class ApplyPatchTask extends Task {

    private static final DateFormat GIT_BRANCH_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_hh-mm-ss");

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

    public ApplyPatchTask(Logger logger, LauncherFactory starter, String baseCommit,
                          String diffID, String conduitToken, String arcPath,
                          String gitPath, boolean createCommit, boolean skipForcedClean,
                          boolean createBranch, boolean patchWithForceFlag) {
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
            int exitCode = starter.launch()
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

            List<String> arcPatchParams = new ArrayList<String>(Arrays.asList("--diff", diffID));
            if (!createCommit) {
                arcPatchParams.add("--nocommit");
            }

            arcPatchParams.add("--nobranch");

            if (patchWithForceFlag) {
                arcPatchParams.add("--force");
            }

            ArcanistClient arc = new ArcanistClient(
                    arcPath,
                    "patch",
                    conduitToken,
                    arcPatchParams.toArray(new String[arcPatchParams.size()]));
            exitCode = arc.callConduit(starter.launch(), logStream);

            if (exitCode == 0 && (createBranch || createCommit)) {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                PrintStream printStream = new PrintStream(outputStream);
                exitCode = starter.launch()
                        .cmds(Arrays.asList(gitPath, "rev-parse", "--abbrev-ref", "HEAD"))
                        .stdout(printStream)
                        .join();
                if (exitCode == 0) {
                    String currentBranchName = new String(outputStream.toByteArray(), "UTF-8");
                    if (currentBranchName.startsWith("arcpatch-D")) {
                        // Rename branch to something that will not conflict when workspace is not cleared
                        String branchName = String.format("diff_%s_%s", diffID, GIT_BRANCH_DATE_FORMAT.format(new Date()));
                        exitCode = starter.launch()
                                .cmds(Arrays.asList(gitPath, "branch", "-m", branchName))
                                .stdout(logStream)
                                .join();

                        if (exitCode != 0) {
                            info("Got non-zero exit code trying to rename branch " + currentBranchName + " to " + branchName + ": " + exitCode);
                        }
                    }
                }
            }
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
