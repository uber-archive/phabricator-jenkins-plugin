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

package com.uber.jenkins.phabricator.uberalls;

import com.uber.jenkins.phabricator.CodeCoverageMetrics;
import com.uber.jenkins.phabricator.conduit.Differential;
import hudson.EnvVars;
import net.sf.json.JSON;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import net.sf.json.groovy.JsonSlurper;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;

public class UberallsClient {
    public static final String PACKAGE_COVERAGE_KEY = "packageCoverage";
    public static final String FILES_COVERAGE_KEY = "filesCoverage";
    public static final String CLASSES_COVERAGE_KEY = "classesCoverage";
    public static final String METHOD_COVERAGE_KEY = "methodCoverage";
    public static final String LINE_COVERAGE_KEY = "lineCoverage";
    public static final String CONDITIONAL_COVERAGE_KEY = "conditionalCoverage";

    private final EnvVars environment;
    private String baseURL;
    private final PrintStream logger;

    public boolean recordCoverage(String currentSHA, String branch, CodeCoverageMetrics codeCoverageMetrics) {
        if (codeCoverageMetrics.isValid()) {
            JSONObject params = new JSONObject();
            params.put("sha", currentSHA);
            params.put("branch", branch);
            params.put(PACKAGE_COVERAGE_KEY, codeCoverageMetrics.getPackageCoveragePercent());
            params.put(FILES_COVERAGE_KEY, codeCoverageMetrics.getFilesCoveragePercent());
            params.put(CLASSES_COVERAGE_KEY, codeCoverageMetrics.getClassesCoveragePercent());
            params.put(METHOD_COVERAGE_KEY, codeCoverageMetrics.getMethodCoveragePercent());
            params.put(LINE_COVERAGE_KEY, codeCoverageMetrics.getLineCoveragePercent());
            params.put(CONDITIONAL_COVERAGE_KEY, codeCoverageMetrics.getConditionalCoveragePercent());
            params.put("repository", this.environment.get("GIT_URL"));

            try {
                Request.Post(getBuilder().build())
                        .bodyString(params.toString(), ContentType.APPLICATION_JSON)
                        .execute().returnContent().asString();
                return true;
            } catch (URISyntaxException e) {
                e.printStackTrace();
            } catch (HttpResponseException e) {
                // e.g. 404, pass
            } catch (ClientProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return false;
    }

    public UberallsClient(String baseURL, EnvVars environment, PrintStream logger) {
        this.baseURL = baseURL;
        this.environment = environment;
        this.logger = logger;
    }

    public String getBaseURL() {
        return this.baseURL;
    }

    public CodeCoverageMetrics getParentCoverage(Differential differential) {
        try {
            String sha = differential.getBaseCommit();
            String coverageJSON = null;
            if (sha != null) {
                coverageJSON = getCoverage("sha", sha);
            }
            JsonSlurper jsonParser = new JsonSlurper();
            JSON responseJSON = jsonParser.parseText(coverageJSON);
            if (responseJSON instanceof JSONNull) {
                return null;
            }
            JSONObject coverage = (JSONObject) responseJSON;

            return new CodeCoverageMetrics(
                    coverage.getString("sha"),
                    ((Double) coverage.getDouble(PACKAGE_COVERAGE_KEY)).floatValue(),
                    ((Double) coverage.getDouble(FILES_COVERAGE_KEY)).floatValue(),
                    ((Double) coverage.getDouble(CLASSES_COVERAGE_KEY)).floatValue(),
                    ((Double) coverage.getDouble(METHOD_COVERAGE_KEY)).floatValue(),
                    ((Double) coverage.getDouble(LINE_COVERAGE_KEY)).floatValue(),
                    ((Double) coverage.getDouble(CONDITIONAL_COVERAGE_KEY)).floatValue());
        } catch (Exception e) {
            e.printStackTrace(logger);
        }

        return null;
    }

    public String getCoverage(String param, String value) {
        URIBuilder builder;
        try {
            builder = getBuilder()
                .setParameter("repository", this.environment.get("GIT_URL"))
                .setParameter(param, value);

            return Request.Get(builder.build())
                    .execute().returnContent().asString();
        } catch (URISyntaxException e) {
            e.printStackTrace(logger);
        } catch (HttpResponseException e) {
            if (e.getStatusCode() != 404) {
                e.printStackTrace(logger);
            }
        } catch (ClientProtocolException e) {
            e.printStackTrace(logger);
        } catch (IOException e) {
            e.printStackTrace(logger);
        }
        return null;
    }

    private URIBuilder getBuilder() throws URISyntaxException {
        return new URIBuilder(baseURL);
    }
}
