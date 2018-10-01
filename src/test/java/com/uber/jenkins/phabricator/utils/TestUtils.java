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

import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.uber.jenkins.phabricator.FakeConduit;
import com.uber.jenkins.phabricator.LauncherFactory;
import com.uber.jenkins.phabricator.PhabricatorPlugin;
import com.uber.jenkins.phabricator.conduit.ConduitAPIClient;
import com.uber.jenkins.phabricator.conduit.DifferentialClient;
import com.uber.jenkins.phabricator.coverage.CodeCoverageMetrics;
import com.uber.jenkins.phabricator.credentials.ConduitCredentials;
import com.uber.jenkins.phabricator.credentials.ConduitCredentialsImpl;
import com.uber.jenkins.phabricator.uberalls.UberallsClient;
import com.uber.jenkins.phabricator.unit.UnitResult;

import net.sf.json.JSONObject;
import net.sf.json.groovy.JsonSlurper;

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.apache.http.localserver.LocalTestServer;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.apache.http.util.EntityUtils;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.plugins.cobertura.CoberturaPublisher;
import hudson.plugins.cobertura.renderers.SourceEncoding;
import hudson.slaves.EnvironmentVariablesNodeProperty;
import hudson.tasks.Publisher;
import hudson.tasks.junit.JUnitResultArchiver;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

public class TestUtils {

    public static final String TEST_BASE_URL = "http://uberalls.example.com";
    public static final String TEST_REPOSITORY = "test-repository";
    public static final String TEST_BRANCH = "test-branch";
    public static final String TEST_SHA = "test-sha";
    public static final String COBERTURA_XML = "cobertura.xml";
    public static final String JUNIT_XML = "junit-test.xml";

    public static final String TEST_DIFFERENTIAL_ID = "123";
    public static final String TEST_CONDUIT_TOKEN = "notarealtoken";
    public static final String TEST_PHID = "PHID-not-real";
    public static final String TEST_CREDENTIALS_ID = "not-a-real-uuid-for-credentials";
    public static final String TEST_CONDUIT_URL = "http://example.gophers";
    public static final String TEST_CONDUIT_GATEWAY = "http://foo.bar";
    public static final String TEST_DESCRIPTION = "foobar";
    private static final String TEST_UNIT_NAMESPACE = "unit namespace";
    private static final String TEST_UNIT_NAME = "fake test name";

    public static Logger getDefaultLogger() {
        return new Logger(new PrintStream(new ByteArrayOutputStream()));
    }

    public static UberallsClient getUberallsClient(
            String baseURL, Logger logger, String repository,
            String branch) {
        return spy(new UberallsClient(baseURL, logger, repository, branch));
    }

    public static UberallsClient getDefaultUberallsClient() {
        return getUberallsClient(TEST_BASE_URL, getDefaultLogger(), TEST_REPOSITORY, TEST_BRANCH);
    }

    public static DifferentialClient getDefaultDifferentialClient() {
        ConduitAPIClient client = mock(ConduitAPIClient.class);
        return spy(new DifferentialClient(TEST_DIFFERENTIAL_ID, client));
    }

    public static EnvVars getDefaultEnvVars() {
        return new EnvVars();
    }

    public static LauncherFactory createLauncherFactory(JenkinsRule j) throws Exception {
        return new LauncherFactory(
                j.createLocalLauncher(),
                getDefaultEnvVars(),
                System.err,
                new FilePath(j.getWebAppRoot())
        );
    }

    public static CodeCoverageMetrics getCodeCoverageMetrics(
            float packagesCoveragePercent,
            float filesCoveragePercent,
            float classesCoveragePercent,
            float methodCoveragePercent,
            float lineCoveragePercent,
            float conditionalCoveragePercent,
            float linesCovered,
            float linesTested) {
        return spy(new CodeCoverageMetrics(packagesCoveragePercent, filesCoveragePercent,
                classesCoveragePercent, methodCoveragePercent, lineCoveragePercent,
                conditionalCoveragePercent, linesCovered, linesTested));
    }

    public static CodeCoverageMetrics getDefaultCodeCoverageMetrics() {
        return getCodeCoverageMetrics(100.0f, 100.0f, 100.0f, 100.0f, 100.0f, 100.0f, 100.0f, 100.0f);
    }

    public static CodeCoverageMetrics getCoverageResult(
            Float packageCoverage, Float filesCoverage,
            Float classesCoverage, Float methodCoverage,
            Float linesCoverage, Float linesCovered,
            Float linesTested) {
        return new CodeCoverageMetrics(
                packageCoverage,
                filesCoverage,
                classesCoverage,
                methodCoverage,
                linesCoverage,
                0.0f,
                linesCovered,
                linesTested
        );
    }

    public static CodeCoverageMetrics getEmptyCoverageMetrics() {
        return new CodeCoverageMetrics(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
    }

    public static JSONObject getJSONFromFile(Class klass, String filename) throws IOException {
        InputStream in = klass.getResourceAsStream(String.format("%s.json", filename));
        return slurpFromInputStream(in);
    }

    private static JSONObject slurpFromInputStream(InputStream in) throws IOException {
        return (JSONObject) new JsonSlurper().parse(in);
    }

    public static HttpRequestHandler makeHttpHandler(final int statusCode, final String body) {
        ArrayList<String> requestBodies = new ArrayList<String>();
        return makeHttpHandler(statusCode, body, requestBodies);
    }

    public static HttpRequestHandler makeHttpHandler(
            final int statusCode, final String body,
            final List<String> requestBodies) {
        return new HttpRequestHandler() {
            @Override
            public void handle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException,
                    IOException {
                response.setStatusCode(statusCode);
                response.setEntity(new StringEntity(body));

                if (request instanceof HttpEntityEnclosingRequest) {
                    HttpEntity entity = ((HttpEntityEnclosingRequest) request).getEntity();
                    requestBodies.add(EntityUtils.toString(entity));
                } else {
                    requestBodies.add("");
                }
            }
        };
    }

    public static void setEnvironmentVariables(JenkinsRule j, Map<String, String> params) throws IOException {
        EnvironmentVariablesNodeProperty prop = new EnvironmentVariablesNodeProperty();
        EnvVars envVars = prop.getEnvVars();
        envVars.putAll(params);
        j.jenkins.getGlobalNodeProperties().add(prop);
    }

    public static Map<String, String> getValidBuildEnvironment(boolean harbormaster) {
        Map<String, String> params = new HashMap<String, String>();
        params.put(PhabricatorPlugin.DIFFERENTIAL_ID_FIELD, TEST_DIFFERENTIAL_ID);
        if (harbormaster) {
            params.put(PhabricatorPlugin.PHID_FIELD, TEST_PHID);
        }
        return params;
    }

    public static Map<String, String> getValidCommitEnvironment() {
        Map<String, String> params = new HashMap<String, String>();
        params.put(PhabricatorPlugin.PHID_FIELD, TEST_PHID);
        return params;
    }

    public static void setDefaultBuildEnvironment(JenkinsRule j) throws IOException {
        setDefaultBuildEnvironment(j, true);
    }

    public static void setDefaultBuildEnvironment(JenkinsRule j, boolean harbormaster) throws IOException {
        setEnvironmentVariables(j, getValidBuildEnvironment(harbormaster));
    }

    public static void setDefaultBuildEnvironmentForCommits(JenkinsRule j) throws IOException {
        setEnvironmentVariables(j, getValidCommitEnvironment());
    }

    public static ConduitCredentials getConduitCredentials(String url, String gateway) {
        return new ConduitCredentialsImpl(TEST_CREDENTIALS_ID, url, gateway, TEST_DESCRIPTION, TEST_CONDUIT_TOKEN);
    }

    public static ConduitCredentials getConduitCredentials(String conduitURI) {
        return getConduitCredentials(conduitURI, conduitURI);
    }

    public static ConduitCredentials getDefaultConduitCredentials() {
        return getConduitCredentials(TEST_CONDUIT_URL);
    }

    private static void addCredentials(ConduitCredentials credentials) throws IOException {
        CredentialsStore store = new SystemCredentialsProvider.UserFacingAction().getStore();
        store.addCredentials(Domain.global(), credentials);
    }

    public static void addInvalidCredentials() throws IOException {
        addCredentials(TestUtils.getDefaultConduitCredentials());
    }

    public static void addValidCredentials(FakeConduit conduit) throws IOException {
        addCredentials(TestUtils.getConduitCredentials(conduit.uri()));
    }

    public static String getTestServerAddress(LocalTestServer server) {
        return String.format(
                "http://%s:%s",
                server.getServiceAddress().getHostName(),
                server.getServiceAddress().getPort()
        );
    }

    public static Map<String, List<Integer>> getDefaultLineCoverage() {
        Map<String, List<Integer>> coverage = new HashMap<String, List<Integer>>();
        List<Integer> lineCoverage = new ArrayList<Integer>(Arrays.asList(
                null,
                2,
                0,
                1
        ));
        coverage.put("file.go", lineCoverage);
        return coverage;
    }

    public static CoberturaPublisher getDefaultCoberturaPublisher() {
        return new CoberturaPublisher(
                COBERTURA_XML,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                SourceEncoding.UTF_8,
                1
        );
    }

    public static UnitResult getDefaultUnitResult() {
        return new UnitResult(
                TEST_UNIT_NAMESPACE,
                TEST_UNIT_NAME,
                null,
                1.0f,
                0,
                0,
                1
        );
    }

    public static Publisher getDefaultXUnitPublisher() {
        return new JUnitResultArchiver(
                JUNIT_XML
        );
    }

    public static void addCopyBuildStep(
            FreeStyleProject p,
            final String fileName,
            final Class resourceClass,
            final String resourceName) {
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild build, Launcher launcher, BuildListener buildListener) throws
                    InterruptedException, IOException {
                build.getWorkspace().child(fileName).copyFrom(resourceClass.getResourceAsStream(resourceName));
                return true;
            }
        });
    }
}
