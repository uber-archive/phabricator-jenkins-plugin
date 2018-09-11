package com.uber.jenkins.phabricator.coverage;

import com.google.common.annotations.VisibleForTesting;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.Project;
import hudson.model.Run;
import hudson.plugins.jacoco.ExecutionFileLoader;
import hudson.plugins.jacoco.JacocoBuildAction;
import hudson.plugins.jacoco.JacocoPublisher;
import hudson.plugins.jacoco.report.CoverageReport;
import hudson.remoting.VirtualChannel;
import jenkins.MasterToSlaveFileCallable;
import org.apache.tools.ant.DirectoryScanner;
import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.analysis.ILine;
import org.jacoco.core.analysis.IPackageCoverage;
import org.jacoco.core.analysis.ISourceFileCoverage;
import org.jacoco.core.analysis.ISourceNode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides Jacoco coverage data
 */
@SuppressWarnings("unused")
public class JacocoCoverageProvider extends CoverageProvider {

    private static final int PERCENTAGE_UNAVAILABLE = -1;

    @Override
    public boolean hasCoverage() {
        CoverageReport result = getCoverageResult();
        return result != null && result.hasLineCoverage();
    }

    private CoverageReport getCoverageResult() {
        JacocoBuildAction jacocoAction = getJacocoBuildAction();
        if (jacocoAction == null) {
            return null;
        }
        return jacocoAction.getResult();
    }

    private JacocoBuildAction getJacocoBuildAction() {
        Run<?, ?> build = getBuild();
        if (build == null) {
            return null;
        }

        return build.getAction(JacocoBuildAction.class);
    }

    private JacocoPublisher getJacocoPublisher() {
        Run<?, ?> run = getBuild();
        if (run == null) {
            return null;
        }
        Project<?,?> project = (Project<?,?>) ((AbstractBuild) run).getProject();
        return (JacocoPublisher) project.getPublisher(JacocoPublisher.DESCRIPTOR);
    }

    @Override
    protected CodeCoverageMetrics getCoverageMetrics() {
        return convertJacoco(getCoverageResult());
    }

    @Override
    public Map<String, List<Integer>> readLineCoverage() {
        JacocoBuildAction jacocoAction = getJacocoBuildAction();
        JacocoPublisher jacocoPublisher = getJacocoPublisher();
        if (jacocoAction == null || jacocoPublisher == null) {
            return null;
        }

        PathResolver pathResolver = new PathResolver(getWorkspace(), getSourceDirs());
        HashMap<String, List<Integer>> lineCoverage = new HashMap<String, List<Integer>>();

        String[] includes = null;
        if (jacocoPublisher.getInclusionPattern() != null) {
            includes = new String[]{ jacocoPublisher.getInclusionPattern() };
        }

        String[] excludes = null;
        if (jacocoPublisher.getExclusionPattern() != null) {
            excludes = new String[]{jacocoPublisher.getExclusionPattern()};
        }

        try {
            ExecutionFileLoader executionFileLoader = jacocoAction.getJacocoReport().parse(includes, excludes);
            for (IPackageCoverage packageCoverage : executionFileLoader.getBundleCoverage().getPackages()) {
                for (ISourceFileCoverage fileCoverage : packageCoverage.getSourceFiles()) {
                    String relativePathFromProjectRoot = getRelativePathFromProjectRoot(pathResolver, fileCoverage);
                    if (relativePathFromProjectRoot != null) {
                        lineCoverage.put(relativePathFromProjectRoot, getPerLineCoverage(fileCoverage));
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return lineCoverage;
    }

    private static String getRelativePathFromProjectRoot(PathResolver pathResolver, ISourceFileCoverage fileCoverage) {
        String relativeSourcePath = fileCoverage.getPackageName() + "/" + fileCoverage.getName();
        Map<String, String> stringMap = pathResolver.choose(Collections.singletonList(relativeSourcePath));
        String dirPath = stringMap.get(relativeSourcePath);
        if (dirPath == null) {
            return null;
        }
        return dirPath + "/" + relativeSourcePath;
    }

    private List<String> getSourceDirs() {
        JacocoPublisher jacocoPublisher = getJacocoPublisher();
        FilePath[] dirPaths = resolveDirPaths(getWorkspace(), jacocoPublisher.getSourcePattern());

        List<String> relativePaths = new ArrayList<String>();
        for (FilePath dirPath : dirPaths) {
            relativePaths.add(makeRelative(dirPath.getRemote()));
        }
        return relativePaths;
    }

    private static List<Integer> getPerLineCoverage(ISourceFileCoverage fileCoverage) {
        List<Integer> perLineCoverages = new ArrayList<Integer>();

        if (fileCoverage.getFirstLine() == ISourceNode.UNKNOWN_LINE ||
                fileCoverage.getLastLine() == ISourceNode.UNKNOWN_LINE) {
            return null;
        }
        for (int i = 1; i <= fileCoverage.getLastLine(); i++) {
            ILine line = fileCoverage.getLine(i);
            perLineCoverages.add(getHitCount(line));
        }
        return perLineCoverages;
    }

    private static Integer getHitCount(ILine line) {
        // Fake hit-count as Jacoco doesn't provide it
        Integer fakeHitCount;
        switch (line.getStatus()) {
            case ICounter.EMPTY:
                fakeHitCount = null;
                break;
            case ICounter.NOT_COVERED:
                fakeHitCount = 0;
                break;
            case ICounter.FULLY_COVERED:
                fakeHitCount = 1;
                break;
            case ICounter.PARTLY_COVERED:
                fakeHitCount = 0; // Harbormaster format doesn't allow for partially covered lines
                break;
            default:
                fakeHitCount = null;
        }
        return fakeHitCount;
    }

    @SuppressWarnings("WeakerAccess")
    @VisibleForTesting
    static CodeCoverageMetrics convertJacoco(CoverageReport coverageResult) {
        if (coverageResult == null) {
            return null;
        }
        float methodCoverage = coverageResult.getMethodCoverage().getPercentageFloat();
        float classCoverage = coverageResult.getClassCoverage().getPercentageFloat();
        float lineCoverage = coverageResult.getLineCoverage().getPercentageFloat();
        float branchCoverage = coverageResult.getBranchCoverage().getPercentageFloat();

        return new CodeCoverageMetrics(
                PERCENTAGE_UNAVAILABLE,
                PERCENTAGE_UNAVAILABLE,
                classCoverage,
                methodCoverage,
                lineCoverage,
                branchCoverage
        );
    }

    private String makeRelative(String srcDir) {
        return srcDir.replaceFirst(getWorkspace() + "/", "");
    }

    // From Jacoco Jenkins plugin
    private static FilePath[] resolveDirPaths(FilePath workspace, final String input) {
        FilePath[] directoryPaths = null;
        try {
            directoryPaths = workspace.act(new MasterToSlaveFileCallable<FilePath[]>() {
                static final long serialVersionUID = 1552178457453558870L;

                public FilePath[] invoke(File f, VirtualChannel channel) throws IOException {
                    FilePath base = new FilePath(f);
                    ArrayList<FilePath> localDirectoryPaths = new ArrayList<FilePath>();
                    String[] includes = input.split(",");
                    DirectoryScanner ds = new DirectoryScanner();

                    ds.setIncludes(includes);
                    ds.setCaseSensitive(false);
                    ds.setBasedir(f);
                    ds.scan();
                    String[] dirs = ds.getIncludedDirectories();

                    for (String dir : dirs) {
                        localDirectoryPaths.add(base.child(dir));
                    }
                    FilePath[] lfp = {};//trick to have an empty array as a parameter, so the returned array will contain the elements
                    return localDirectoryPaths.toArray(lfp);
                }
            });

        } catch (InterruptedException ie) {
            ie.printStackTrace();
        } catch (IOException io) {
            io.printStackTrace();
        }
        return directoryPaths;
    }

}
