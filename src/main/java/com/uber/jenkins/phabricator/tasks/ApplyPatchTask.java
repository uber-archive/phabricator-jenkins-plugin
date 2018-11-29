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
import java.util.Collections;
import java.util.List;

public class ApplyPatchTask extends Task {
    private static final String DEFAULT_GIT_PATH = "git";
    private static final String DEFAULT_HG_PATH = "hg";

    private final LauncherFactory starter;
    private final PrintStream logStream;

    private final String scmType;

    private String gitPath;
    private String hgPath;
    private final String arcPath;

    private final boolean skipForcedClean;

    private final String baseCommit;

    private final String conduitUrl;
    private final String conduitToken;

    private final String diffID;
    private final boolean createCommit;
    private final boolean createBranch;
    private final boolean patchWithForceFlag;

    public ApplyPatchTask(
            Logger logger, LauncherFactory starter, String baseCommit,
            String diffID, String conduitUrl, String conduitToken, String arcPath,
            boolean createCommit,
            boolean skipForcedClean, boolean createBranch,
            boolean patchWithForceFlag, String scmType) {
        super(logger);

        this.arcPath = arcPath;
        this.gitPath = DEFAULT_GIT_PATH;
        this.hgPath = DEFAULT_HG_PATH;

        this.scmType = scmType;

        this.starter = starter;
        this.logStream = logger.getStream();

        this.baseCommit = baseCommit;
        this.diffID = diffID;
        this.conduitUrl = conduitUrl;
        this.conduitToken = conduitToken;

        this.createCommit = createCommit;
        this.skipForcedClean = skipForcedClean;
        this.createBranch = createBranch;
        this.patchWithForceFlag = patchWithForceFlag;
    }

    /**
     * Allows to override default exeturable path used for git
     */
    void setGitPath(String gitPath) {
        this.gitPath = gitPath;
    }

    /**
     * Allows to override default exeturable path used for git
     */
    void setHgPath(String hgPath) {
        this.hgPath = hgPath;
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
            prepareRepositoryState();
            this.result = applyArcPatch() == 0 ? Result.SUCCESS : Result.FAILURE;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace(logStream);
            this.result = Result.FAILURE;
        }
    }

    private int applyArcPatch() throws IOException, InterruptedException {
        int exitCode;
        List<String> arcPatchParams = new ArrayList<String>(Arrays.asList("--diff", diffID));

        switch (scmType) {
            case "git":
            case "hg":
                if (!createCommit) {
                    arcPatchParams.add("--nocommit");
                }

                if (!createBranch) {
                    arcPatchParams.add("--nobranch");
                }
                break;
            case "svn":
                break;
            default:
                info("Unknown scm type " + scmType + " skipping");
        }

        if (patchWithForceFlag) {
            arcPatchParams.add("--force");
        }

        ArcanistClient arc = new ArcanistClient(
                arcPath,
                "patch",
                conduitUrl,
                conduitToken,
                arcPatchParams.toArray(new String[arcPatchParams.size()]));

        exitCode = arc.callConduit(starter.launch(), logStream);
        return exitCode;
    }

    @SuppressWarnings("UnusedReturnValue")
    private void prepareRepositoryState() throws InterruptedException, IOException {
        List<String> resetToBaseCommit = Collections.emptyList();
        List<String> cleanWorkingDir = Collections.emptyList();
        List<String> updateSubmodules = Collections.emptyList();

        switch (scmType) {
            case "git":
                resetToBaseCommit = Arrays.asList(gitPath, "reset", "--hard", baseCommit);
                cleanWorkingDir = Arrays.asList(gitPath, "clean", "-fd", "-f");
                updateSubmodules = Arrays.asList(gitPath, "submodule", "update", "--init", "--recursive");
                break;
            case "hg":
                resetToBaseCommit = Arrays.asList(hgPath, "update", "--clean", baseCommit);
                // Purge is core extension but not enabled by default
                cleanWorkingDir = Arrays.asList(hgPath, "--config", "extensions.purge=", "purge", "--files", "--dirs");
                // Submodules updated by resetToBaseCommit command
                updateSubmodules = Collections.emptyList();
                break;
            case "svn":
                break;
            default:
                info("Unknown scm type " + scmType + " skipping");
                return;
        }

        int exitCode = launch(resetToBaseCommit);

        if (exitCode != 0) {
            info("Got non-zero exit code resetting to base commit " + baseCommit + ": " + exitCode);
        }

        if (!skipForcedClean) {
            launch(cleanWorkingDir);
        }

        launch(updateSubmodules);
    }

    private int launch(List<String> cmds) throws IOException, InterruptedException {
        if (cmds.isEmpty()) {
            return 0;
        }
        return starter.launch().cmds(cmds).stdout(logStream).join();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void tearDown() {
        // Do nothing
    }
}
