package com.uber.jenkins.phabricator.uberalls;

import com.uber.jenkins.phabricator.coverage.CodeCoverageMetrics;
import com.uber.jenkins.phabricator.utils.TestUtils;

import net.sf.json.JSONObject;

import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.localserver.LocalServerTestBase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

public class UberallsClientTest extends LocalServerTestBase {

    private UberallsClient client;

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        this.shutDown();
    }

    @Test
    public void testGetCoverageNotFound() throws Exception {
        this.start();
        this.client = getDefaultClient();
        assertNull(client.getCoverage(TestUtils.TEST_SHA));
    }

    @Test
    public void testGetCoverageFound() throws Exception {
        this.serverBootstrap.registerHandler("/*", TestUtils.makeHttpHandler(HttpStatus.SC_OK, "{}"));
        this.start();
        this.client = getDefaultClient();
        String coverage = client.getCoverage(TestUtils.TEST_SHA);
        assertEquals("{}", coverage);
    }

    @Test
    public void testGetCoverageInternalError() throws Exception {
        this.serverBootstrap.registerHandler("/*", TestUtils.makeHttpHandler(HttpStatus.SC_INTERNAL_SERVER_ERROR, ""));
        this.start();
        this.client = getDefaultClient();
        assertNull(client.getCoverage(TestUtils.TEST_SHA));
    }

    @Test
    public void testGetParentCoverageNoBackend() throws Exception {
        this.start();
        this.client = getDefaultClient();
        assertNull(client.getParentCoverage(TestUtils.TEST_SHA));
    }

    @Test
    public void testGetParentCoverageNullSHA() throws Exception {
        this.start();
        this.client = getDefaultClient();
        assertNull(client.getParentCoverage(null));
    }

    @Test
    public void testGetParentCoverageNull() throws Exception {
        this.serverBootstrap.registerHandler("/*", TestUtils.makeHttpHandler(HttpStatus.SC_OK, "null"));
        this.start();
        this.client = getDefaultClient();
        assertNull(client.getParentCoverage(TestUtils.TEST_SHA));
    }

    @Test
    public void testGetCoverageMissingKey() throws Exception {
        this.serverBootstrap.registerHandler("/*", TestUtils.makeHttpHandler(HttpStatus.SC_OK, "{}"));
        this.start();
        this.client = getDefaultClient();
        assertNull(client.getParentCoverage(TestUtils.TEST_SHA));
    }

    @Test
    public void testGetCoverageWorkingBackend() throws Exception {
        JSONObject validJSON = TestUtils.getJSONFromFile(getClass(), "validCoverage");
        this.serverBootstrap.registerHandler("/*", TestUtils.makeHttpHandler(HttpStatus.SC_OK, validJSON.toString()));
        this.start();
        this.client = getDefaultClient();
        CodeCoverageMetrics metrics = client.getParentCoverage(TestUtils.TEST_SHA);
        assertEquals(42.0f, metrics.getLineCoveragePercent(), 0.01f);
    }

    @Test
    public void testRecordCoverageNullMetrics() throws Exception {
        this.start();
        this.client = getDefaultClient();
        assertFalse(client.recordCoverage(TestUtils.TEST_SHA, null));
    }

    @Test
    public void testRecordCoverageInternalError() throws Exception {
        this.serverBootstrap.registerHandler("/*", TestUtils.makeHttpHandler(HttpStatus.SC_INTERNAL_SERVER_ERROR, ""));
        this.start();
        this.client = getDefaultClient();
        assertFalse(client.recordCoverage(TestUtils.TEST_SHA, TestUtils.getDefaultCodeCoverageMetrics()));
    }

    @Test
    public void testRecordCoverageSuccessful() throws Exception {
        this.serverBootstrap.registerHandler("/*", TestUtils.makeHttpHandler(HttpStatus.SC_OK, "{}"));
        this.start();
        this.client = getDefaultClient();
        assertTrue(client.recordCoverage(TestUtils.TEST_SHA, TestUtils.getDefaultCodeCoverageMetrics()));
    }

    @Test
    public void testRecordCoverageHttpResponseException() throws Exception {
        assertRecordCoverageException(HttpResponseException.class);
    }

    @Test
    public void testRecordCoverageClientProtocolException() throws Exception {
        assertRecordCoverageException(ClientProtocolException.class);
    }

    @Test
    public void testRecordCoverageIOException() throws Exception {
        assertRecordCoverageException(IOException.class);
    }

    @Test
    public void testGetCoverageHttpResponseException() throws Exception {
        assertGetCoverageException(HttpResponseException.class);
    }

    @Test
    public void testGetCoverageRandomException() throws Exception {
        assertGetCoverageException(IOException.class);
    }

    private void assertRecordCoverageException(Class<? extends Exception> exceptionClass) throws Exception {
        CloseableHttpClient mockClient = mockClient();

        doThrow(exceptionClass).when(mockClient).execute(any(HttpRequestBase.class));
        assertFalse(client.recordCoverage(TestUtils.TEST_SHA, TestUtils.getDefaultCodeCoverageMetrics()));
    }

    private void assertGetCoverageException(Class<? extends Exception> exceptionClass) throws Exception {
        CloseableHttpClient mockClient = mockClient();

        doThrow(exceptionClass).when(mockClient).execute(any(HttpRequestBase.class));
        assertNull(client.getCoverage(TestUtils.TEST_SHA));
    }

    private UberallsClient getDefaultClient() {
        return spy(new UberallsClient(
                getTestServerAddress(),
                TestUtils.getDefaultLogger(),
                TestUtils.TEST_REPOSITORY,
                TestUtils.TEST_BRANCH
        ));
    }

    private CloseableHttpClient getMockHttpClient() {
        return mock(CloseableHttpClient.class);
    }

    private CloseableHttpClient mockClient() throws Exception {
        this.start();
        this.client = getDefaultClient();
        CloseableHttpClient mockClient = getMockHttpClient();
        doReturn(mockClient).when(client).getClient();

        return mockClient;
    }

    private String getTestServerAddress() {
        return TestUtils.getTestServerAddress(this.server);
    }
}
