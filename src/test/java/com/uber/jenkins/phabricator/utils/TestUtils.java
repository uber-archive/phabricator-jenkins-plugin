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

import com.uber.jenkins.phabricator.coverage.CodeCoverageMetrics;
import com.uber.jenkins.phabricator.LauncherFactory;
import com.uber.jenkins.phabricator.conduit.ConduitAPIClient;
import com.uber.jenkins.phabricator.conduit.DifferentialClient;
import com.uber.jenkins.phabricator.uberalls.UberallsClient;
import hudson.EnvVars;
import hudson.FilePath;
import net.sf.json.JSONObject;
import net.sf.json.groovy.JsonSlurper;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

public class TestUtils {

    public static final String TEST_BASE_URL = "http://uberalls.example.com";
    public static final String TEST_REPOSITORY = "test-repository";
    public static final String TEST_BRANCH = "test-branch";
    public static final String TEST_SHA = "test-sha";

    public static final String TEST_DIFFERENTIAL_ID = "123";
    public static final String TEST_CONDUIT_TOKEN = "notarealtoken";
    public static final String TEST_PHID = "PHID-not-real";

    public static Logger getDefaultLogger() {
        return new Logger(new PrintStream(new ByteArrayOutputStream()));
    }

    public static UberallsClient getUberallsClient(String baseURL, Logger logger, String repository,
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

    public static CodeCoverageMetrics getCodeCoverageMetrics(float packagesCoveragePercent,
                                                             float filesCoveragePercent,
                                                             float classesCoveragePercent,
                                                             float methodCoveragePercent,
                                                             float lineCoveragePercent,
                                                             float conditionalCoveragePercent) {
        return spy(new CodeCoverageMetrics(packagesCoveragePercent, filesCoveragePercent,
                classesCoveragePercent, methodCoveragePercent, lineCoveragePercent,
                conditionalCoveragePercent));
    }

    public static CodeCoverageMetrics getDefaultCodeCoverageMetrics() {
        return getCodeCoverageMetrics(100.0f, 100.0f, 100.0f, 100.0f, 100.0f, 100.0f);
    }

    public static CodeCoverageMetrics getCoverageResult(Float packageCoverage, Float filesCoverage,
                                                        Float classesCoverage, Float methodCoverage,
                                                        Float linesCoverage) {
        return new CodeCoverageMetrics(
                packageCoverage,
                filesCoverage,
                classesCoverage,
                methodCoverage,
                linesCoverage,
                0.0f
        );
    }

    public static CodeCoverageMetrics getEmptyCoverageMetrics() {
        return new CodeCoverageMetrics(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
    }

    public static JSONObject getJSONFromFile(Class klass, String filename) throws IOException {
        InputStream in = klass.getResourceAsStream(String.format("%s.json", filename));
        return slurpFromInputStream(in);
    }

    private static JSONObject slurpFromInputStream(InputStream in) throws IOException {
        return (JSONObject) new JsonSlurper().parse(in);
    }

    public static HttpRequestHandler makeHttpHandler(final int statusCode, final String body) {
        return new HttpRequestHandler() {
            @Override
            public void handle(HttpRequest request, HttpResponse response, HttpContext context) throws HttpException, IOException {
                response.setStatusCode(statusCode);
                response.setEntity(new StringEntity(body));

            }
        };
    }
}
