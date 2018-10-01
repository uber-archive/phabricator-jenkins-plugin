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

import com.uber.jenkins.phabricator.coverage.CodeCoverageMetrics;
import com.uber.jenkins.phabricator.utils.CommonUtils;
import com.uber.jenkins.phabricator.utils.Logger;

import net.sf.json.JSON;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import net.sf.json.groovy.JsonSlurper;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;

import java.io.IOException;
import java.net.URISyntaxException;

public class UberallsClient {

    public static final String PACKAGE_COVERAGE_KEY = "packageCoverage";
    public static final String FILES_COVERAGE_KEY = "filesCoverage";
    public static final String CLASSES_COVERAGE_KEY = "classesCoverage";
    public static final String METHOD_COVERAGE_KEY = "methodCoverage";
    public static final String LINE_COVERAGE_KEY = "lineCoverage";
    public static final String CONDITIONAL_COVERAGE_KEY = "conditionalCoverage";
    public static final String LINES_COVERED_KEY = "linesCovered";
    public static final String LINES_TESTED_KEY = "linesTested";

    private static final String TAG = "uberalls-client";

    private final String baseURL;
    private final Logger logger;
    private final String repository;
    private final String branch;

    public UberallsClient(String baseURL, Logger logger, String repository, String branch) {
        this.baseURL = baseURL;
        this.logger = logger;
        this.repository = repository;
        this.branch = branch;
    }

    public String getBaseURL() {
        return this.baseURL;
    }

    public CodeCoverageMetrics getParentCoverage(String sha) {
        if (sha == null) {
            return null;
        }
        try {
            String coverageJSON = getCoverage(sha);
            JsonSlurper jsonParser = new JsonSlurper();
            JSON responseJSON = jsonParser.parseText(coverageJSON);
            if (responseJSON instanceof JSONNull) {
                return null;
            }
            JSONObject coverage = (JSONObject) responseJSON;

            return new CodeCoverageMetrics(
                    ((Double) coverage.getDouble(PACKAGE_COVERAGE_KEY)).floatValue(),
                    ((Double) coverage.getDouble(FILES_COVERAGE_KEY)).floatValue(),
                    ((Double) coverage.getDouble(CLASSES_COVERAGE_KEY)).floatValue(),
                    ((Double) coverage.getDouble(METHOD_COVERAGE_KEY)).floatValue(),
                    ((Double) coverage.getDouble(LINE_COVERAGE_KEY)).floatValue(),
                    ((Double) coverage.getDouble(CONDITIONAL_COVERAGE_KEY)).floatValue(),
                    ((Double) coverage.getDouble(LINES_COVERED_KEY)).floatValue(),
                    ((Double) coverage.getDouble(LINES_TESTED_KEY)).floatValue());
        } catch (Exception e) {
            e.printStackTrace(logger.getStream());
        }

        return null;
    }

    public boolean recordCoverage(String sha, CodeCoverageMetrics codeCoverageMetrics) {
        if (codeCoverageMetrics != null) {
            JSONObject params = new JSONObject();
            params.put("sha", sha);
            params.put("branch", branch);
            params.put("repository", repository);
            params.put(PACKAGE_COVERAGE_KEY, codeCoverageMetrics.getPackageCoveragePercent());
            params.put(FILES_COVERAGE_KEY, codeCoverageMetrics.getFilesCoveragePercent());
            params.put(CLASSES_COVERAGE_KEY, codeCoverageMetrics.getClassesCoveragePercent());
            params.put(METHOD_COVERAGE_KEY, codeCoverageMetrics.getMethodCoveragePercent());
            params.put(LINE_COVERAGE_KEY, codeCoverageMetrics.getLineCoveragePercent());
            params.put(CONDITIONAL_COVERAGE_KEY, codeCoverageMetrics.getConditionalCoveragePercent());
            params.put(LINES_COVERED_KEY, codeCoverageMetrics.getLinesCovered());
            params.put(LINES_TESTED_KEY, codeCoverageMetrics.getLinesTested());

            try {
                HttpClient client = getClient();
                PostMethod request = new PostMethod(getBuilder().build().toString());
                request.addRequestHeader("Content-Type", "application/json");
                StringRequestEntity requestEntity = new StringRequestEntity(
                        params.toString(),
                        ContentType.APPLICATION_JSON.toString(),
                        "UTF-8");
                request.setRequestEntity(requestEntity);
                int statusCode = client.executeMethod(request);

                if (statusCode != HttpStatus.SC_OK) {
                    logger.info(TAG, "Call failed: " + request.getStatusLine());
                    return false;
                }
                return true;
            } catch (URISyntaxException e) {
                e.printStackTrace(logger.getStream());
            } catch (HttpResponseException e) {
                // e.g. 404, pass
                logger.info(TAG, "HTTP Response error recording metrics: " + e);
            } catch (ClientProtocolException e) {
                e.printStackTrace(logger.getStream());
            } catch (IOException e) {
                e.printStackTrace(logger.getStream());
            }
        }

        return false;
    }

    public String getCoverage(String sha) {
        URIBuilder builder;
        try {
            builder = getBuilder()
                    .setParameter("sha", sha)
                    .setParameter("repository", repository);

            HttpClient client = getClient();
            HttpMethod request = new GetMethod(builder.build().toString());
            int statusCode = client.executeMethod(request);

            if (statusCode != HttpStatus.SC_OK) {
                logger.info(TAG, "Call failed: " + request.getStatusLine());
                return null;
            }
            return request.getResponseBodyAsString();
        } catch (HttpResponseException e) {
            if (e.getStatusCode() != 404) {
                e.printStackTrace(logger.getStream());
            }
        } catch (Exception e) {
            e.printStackTrace(logger.getStream());
        }
        return null;
    }

    private URIBuilder getBuilder() throws URISyntaxException {
        return new URIBuilder(baseURL);
    }

    public HttpClient getClient() {
        return new HttpClient();
    }

    public boolean isConfigured() {
        return !CommonUtils.isBlank(baseURL);
    }
}
