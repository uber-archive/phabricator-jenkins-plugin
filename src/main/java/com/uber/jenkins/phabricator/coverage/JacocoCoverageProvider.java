package com.uber.jenkins.phabricator.coverage;

import com.google.common.annotations.VisibleForTesting;

import org.jacoco.core.analysis.ICounter;
import org.jacoco.core.analysis.ILine;
import org.jacoco.core.analysis.IPackageCoverage;
import org.jacoco.core.analysis.ISourceFileCoverage;
import org.jacoco.core.analysis.ISourceNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import hudson.model.AbstractBuild;
import hudson.model.Project;
import hudson.model.Run;
import hudson.plugins.jacoco.ExecutionFileLoader;
import hudson.plugins.jacoco.JacocoBuildAction;
import hudson.plugins.jacoco.JacocoPublisher;
import hudson.plugins.jacoco.report.CoverageReport;

/**
 * Provides Jacoco coverage data
 */
@SuppressWarnings("unused")
public class JacocoCoverageProvider extends CoverageProvider {

    private static final int PERCENTAGE_UNAVAILABLE = -1;

    public JacocoCoverageProvider(
            Run<?, ?> build,
            Set<String> includeFiles, String coverageReportPattern) {
        super(build, includeFiles, coverageReportPattern);
    }

    private static List<Integer> getPerLineCoverage(ISourceNode fileCoverage) {
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
        float linesCovered = coverageResult.getLineCoverage().getCovered();
        float linesTested = coverageResult.getLineCoverage().getTotal();

        return new CodeCoverageMetrics(
                PERCENTAGE_UNAVAILABLE,
                PERCENTAGE_UNAVAILABLE,
                classCoverage,
                methodCoverage,
                lineCoverage,
                branchCoverage,
                linesCovered,
                linesTested
        );
    }

    private CoverageReport getCoverageResult() {
        if (build == null) {
            return null;
        }

        JacocoBuildAction jacocoAction = getJacocoBuildAction();
        if (jacocoAction == null) {
            return null;
        }
        return jacocoAction.getResult();
    }

    private JacocoBuildAction getJacocoBuildAction() {
        return build.getAction(JacocoBuildAction.class);
    }

    private JacocoPublisher getJacocoPublisher() {
        Project<?, ?> project = (Project<?, ?>) ((AbstractBuild) build).getProject();
        return (JacocoPublisher) project.getPublisher(JacocoPublisher.DESCRIPTOR);
    }

    @Override
    public Map<String, List<Integer>> readLineCoverage() {
        JacocoBuildAction jacocoAction = getJacocoBuildAction();
        JacocoPublisher jacocoPublisher = getJacocoPublisher();
        if (jacocoAction == null || jacocoPublisher == null) {
            return null;
        }

        HashMap<String, List<Integer>> lineCoverage = new HashMap<String, List<Integer>>();

        String[] includes = null;
        if (jacocoPublisher.getInclusionPattern() != null) {
            includes = new String[] {jacocoPublisher.getInclusionPattern()};
        }

        String[] excludes = null;
        if (jacocoPublisher.getExclusionPattern() != null) {
            excludes = new String[] {jacocoPublisher.getExclusionPattern()};
        }

        try {
            ExecutionFileLoader executionFileLoader = jacocoAction.getJacocoReport().parse(includes, excludes);
            for (IPackageCoverage packageCoverage : executionFileLoader.getBundleCoverage().getPackages()) {
                for (ISourceFileCoverage fileCoverage : packageCoverage.getSourceFiles()) {
                    String relativePathFromProjectRoot = getRelativePathFromProjectRoot(
                            fileCoverage.getPackageName() + "/" + fileCoverage.getName());
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

    @Override
    public boolean hasCoverage() {
        CoverageReport result = getCoverageResult();
        return result != null && result.hasLineCoverage();
    }

    @Override
    protected CodeCoverageMetrics getCoverageMetrics() {
        return convertJacoco(getCoverageResult());
    }
}
