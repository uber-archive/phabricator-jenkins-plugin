// Copyright (c) 2015 Uber
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
import hudson.model.Run;
import hudson.plugins.cobertura.CoberturaBuildAction;
import hudson.plugins.cobertura.CoberturaCoverageParser;
import hudson.plugins.cobertura.CoberturaPublisher;
import hudson.plugins.cobertura.Ratio;
import hudson.plugins.cobertura.targets.CoverageMetric;
import hudson.plugins.cobertura.targets.CoverageResult;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provide Cobertura coverage data
 */
@SuppressWarnings("unused")
public class CoberturaCoverageProvider extends CoverageProvider {

    private static final Logger LOGGER = Logger.getLogger(CoberturaCoverageProvider.class.getName());
    private static final String DEFAULT_COVERAGE_REPORT_PATTERN = "**/coverage*.xml, **/cobertura*.xml";

    private CoverageResult mCoverageResult = null;
    private Map<String, List<Integer>> mLineCoverage = null;
    private boolean mHasComputedCoverage = false;

    @Override
    public boolean hasCoverage() {
        if (!mHasComputedCoverage) {
            computeCoverage();
        }
        return mCoverageResult != null && mCoverageResult.getCoverage(CoverageMetric.LINE) != null;
    }

    @Override
    protected CodeCoverageMetrics getCoverageMetrics() {
        if (!mHasComputedCoverage) {
            computeCoverage();
        }
        return convertCobertura(mCoverageResult);
    }

    @Override
    public Map<String, List<Integer>> readLineCoverage() {
        if (!mHasComputedCoverage) {
            computeCoverage();
        }
        return mLineCoverage;
    }

    Map<String, List<Integer>> parseReports(CoberturaXMLParser parser, File[] reports) {
        if (reports == null) {
            return null;
        }
        try {
            return parser.parse(reports);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void computeCoverage() {
        Run<?, ?> build = getBuild();
        FilePath workspace = getWorkspace();
        if (build == null) {
            mHasComputedCoverage = true;
            return;
        }

        // Check if there is a cobertura build action
        CoberturaBuildAction coberturaAction = build.getAction(CoberturaBuildAction.class);
        if (coberturaAction != null) {
            mCoverageResult = coberturaAction.getResult();
            if (mCoverageResult != null) {
                computeLineCoverage();
            }
            mHasComputedCoverage = true;
            return;
        }

        // Fallback to scanning for the reports
        copyCoverageToJenkinsMaster(build, workspace);
        File[] reports = getCoberturaReports(build);
        CoverageResult result = null;
        if (reports != null) {
            for (File report : reports) {
                try {
                    result = CoberturaCoverageParser.parse(report, result);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to load " + report, e);
                }
            }
        }

        if (result != null) {
            result.setOwner(build);
            computeLineCoverage();
            cleanupCoverageFilesOnJenkinsMaster();
        }

        if (result != null) {
            result.setOwner(build);
        }

        mCoverageResult = result;
        mHasComputedCoverage = true;
    }

    private void computeLineCoverage() {
        FilePath workspace = getWorkspace();
        File[] reports = getCoberturaReports(getBuild());
        CoberturaXMLParser parser = new CoberturaXMLParser(workspace, getIncludeFileNames());
        mLineCoverage = parseReports(parser, reports);
    }

    private void copyCoverageToJenkinsMaster(Run<?, ?> build, FilePath workspace) {
        final FilePath moduleRoot = workspace;
        final File buildCoberturaDir = build.getRootDir();
        FilePath buildTarget = new FilePath(buildCoberturaDir);

        String coverageReportPattern = getCoverageReportPattern();
        if (coverageReportPattern == null || coverageReportPattern.isEmpty()) {
            coverageReportPattern = DEFAULT_COVERAGE_REPORT_PATTERN;
        }
        if (moduleRoot != null) {
            try {
                List<FilePath> reports = Arrays.asList(moduleRoot.list(coverageReportPattern));

                int i = 0;
                for (FilePath report : reports) {
                    final FilePath targetPath = new FilePath(buildTarget, "coverage" + (i == 0 ? "" : i) + ".xml");
                    report.copyTo(targetPath);
                    i++;
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                LOGGER.log(Level.WARNING, "Unable to copy coverage to " + buildTarget);
            } catch (IOException e) {
                e.printStackTrace();
                LOGGER.log(Level.WARNING, "Unable to copy coverage to " + buildTarget);
            }
        }
    }

    private void cleanupCoverageFilesOnJenkinsMaster() {
        File[] reports = getCoberturaReports(getBuild());
        if (reports != null) {
            for (File report : reports) {
                report.delete();
            }
        }
    }

    /**
     * Convert Cobertura results to an internal CodeCoverageMetrics representation
     *
     * @param result The cobertura report
     * @return The internal representation of coverage
     */
    public static CodeCoverageMetrics convertCobertura(CoverageResult result) {
        if (result == null) {
            return null;
        }

        float packagesCoverage = getCoveragePercentage(result, CoverageMetric.PACKAGES);
        float filesCoverage = getCoveragePercentage(result, CoverageMetric.FILES);
        float classesCoverage = getCoveragePercentage(result, CoverageMetric.CLASSES);
        float methodCoverage = getCoveragePercentage(result, CoverageMetric.METHOD);
        float lineCoverage = getCoveragePercentage(result, CoverageMetric.LINE);
        float conditionalCoverage = getCoveragePercentage(result, CoverageMetric.CONDITIONAL);

        return new CodeCoverageMetrics(
                packagesCoverage,
                filesCoverage,
                classesCoverage,
                methodCoverage,
                lineCoverage,
                result.getCoverage(CoverageMetric.LINE).numerator,
                result.getCoverage(CoverageMetric.LINE).denominator,
                conditionalCoverage
        );
    }

    private static float getCoveragePercentage(CoverageResult result, CoverageMetric metric) {
        Ratio ratio = result.getCoverage(metric);
        if (ratio == null) {
            return 0.0f;
        }
        return ratio.getPercentageFloat();
    }

    private File[] getCoberturaReports(Run<?, ?> build) {
        return build.getRootDir().listFiles(CoberturaPublisher.COBERTURA_FILENAME_FILTER);
    }
}
