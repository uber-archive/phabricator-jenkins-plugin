package com.uber.jenkins.phabricator.coverage;

import com.google.common.collect.Lists;
import com.google.common.io.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.WithoutJenkins;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.jacoco.JacocoBuildAction;
import hudson.plugins.jacoco.JacocoHealthReportThresholds;
import hudson.plugins.jacoco.JacocoPublisher;
import hudson.plugins.jacoco.JacocoReportDir;
import hudson.plugins.jacoco.model.Coverage;
import hudson.plugins.jacoco.report.CoverageReport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@PrepareForTest({CoverageReport.class, Run.class, JacocoBuildAction.class})
@RunWith(PowerMockRunner.class)
public class JacocoCoverageProviderTest {

    private static final String JAVA_FILE_PATH = "src/main/java/com/petehouston/greet/Greet.java";
    private static final ArrayList<Integer> GREET_EXPECTED_COVERAGE = Lists.newArrayList(null, null, null, null, 1,
            null, null, null, 1, null, null, null, null, null, 0);

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    @WithoutJenkins
    public final void testConvertJacoco() {
        CoverageReport result = getMockResult();
        CodeCoverageMetrics metrics = JacocoCoverageProvider.convertJacoco(result);
        assertEquals(60.0f, metrics.getMethodCoveragePercent(), 0.1f);
        assertEquals(80.0f, metrics.getClassesCoveragePercent(), 0.1f);
        assertEquals(75.0f, metrics.getLineCoveragePercent(), 0.1f);
        assertEquals(50.0f, metrics.getConditionalCoveragePercent(), 0.1f);
    }

    @Test
    @WithoutJenkins
    public void testConvertNullJacoco() {
        assertNull(JacocoCoverageProvider.convertJacoco(null));
    }

    @Test
    @WithoutJenkins
    public void testGetMetricsNullBuild() {
        JacocoCoverageProvider provider = new JacocoCoverageProvider(null, null, null);
        assertNull(provider.getMetrics());
    }

    @Test
    @WithoutJenkins
    public final void testCoverageMetrics() {
        Run mockRun = mock(AbstractBuild.class);

        JacocoBuildAction mockBuildAction = mock(JacocoBuildAction.class);

        when(mockRun.getAction(JacocoBuildAction.class)).thenReturn(mockBuildAction);

        CoverageReport mockResult = getMockResult();
        when(mockBuildAction.getResult()).thenReturn(mockResult);

        JacocoCoverageProvider provider = new JacocoCoverageProvider(mockRun, null, null);

        assertTrue(provider.hasCoverage());
        assertEquals(75.0, provider.getCoverageMetrics().getLineCoveragePercent(), 0.1);
    }

    @Test
    public void testGetMetricsNoResult() throws IOException {
        JacocoCoverageProvider provider = new JacocoCoverageProvider(getEmptyBuild(), null, null);

        assertNull(provider.getMetrics());
        assertFalse(provider.hasCoverage());
    }

    @Test
    public void testGetMetricsWithResult() throws Exception {
        FreeStyleBuild executedJacocoBuild = getExecutedJacocoBuild();
        JacocoReportDir jacocoReportDir = createJacocoReportDir(executedJacocoBuild);

        JacocoBuildAction buildAction = JacocoBuildAction.load(executedJacocoBuild, new JacocoHealthReportThresholds(),
                TaskListener.NULL, jacocoReportDir, null, null);
        executedJacocoBuild.replaceAction(buildAction);

        JacocoCoverageProvider provider = new JacocoCoverageProvider(executedJacocoBuild,
                Collections.singleton(JAVA_FILE_PATH), null);

        assertTrue(provider.hasCoverage());
        assertEquals(66.6, provider.getCoverageMetrics().getLineCoveragePercent(), 0.1);

        FilePath sourcePath = executedJacocoBuild.getWorkspace().child(JAVA_FILE_PATH);
        sourcePath.mkdirs();
        sourcePath.touch(0);

        List<Integer> greetCoverage = provider.readLineCoverage().get(JAVA_FILE_PATH);
        assertEquals(GREET_EXPECTED_COVERAGE, greetCoverage);
    }

    private CoverageReport getMockResult() {
        CoverageReport report = PowerMockito.mock(CoverageReport.class);
        when(report.getMethodCoverage()).thenReturn(new Coverage(40, 60));
        when(report.getClassCoverage()).thenReturn(new Coverage(20, 80));
        when(report.getLineCoverage()).thenReturn(new Coverage(25, 75));
        when(report.getBranchCoverage()).thenReturn(new Coverage(50, 50));

        when(report.hasLineCoverage()).thenReturn(true);
        return report;
    }

    private JacocoReportDir createJacocoReportDir(FreeStyleBuild build) throws IOException, InterruptedException,
            URISyntaxException {
        File classesDir = Files.createTempDir();
        classesDir.deleteOnExit();
        File classPath = new File(classesDir, "com/petehouston/greet/Greet.class");
        Files.createParentDirs(classPath);
        File clazz = new File(getClass().getResource("Greet.class").toURI());
        Files.copy(clazz, classPath);

        JacocoReportDir jacocoReportDir = new JacocoReportDir(build.getRootDir());
        jacocoReportDir.addExecFiles(
                Collections.singleton(new FilePath(new File(getClass().getResource("jacoco.exec").toURI()))));
        jacocoReportDir.saveClassesFrom(new FilePath(classesDir), "**/*.class");

        return jacocoReportDir;
    }

    private FreeStyleBuild getEmptyBuild() throws IOException {
        return new FreeStyleBuild(j.createFreeStyleProject());
    }

    private FreeStyleBuild getExecutedJacocoBuild() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject();
        project.getPublishersList().add(new JacocoPublisher());

        return project.scheduleBuild2(0).get(100, TimeUnit.MINUTES);
    }
}
