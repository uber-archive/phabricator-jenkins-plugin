package com.uber.jenkins.phabricator.uberalls;

import com.uber.jenkins.phabricator.coverage.CodeCoverageMetrics;
import com.uber.jenkins.phabricator.utils.TestUtils;

import net.sf.json.JSONObject;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpResponseException;
import org.apache.http.localserver.LocalTestServer;
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

public class UberallsClientTest {

    private LocalTestServer server;
    private UberallsClient client;

    @Before
    public void setUp() throws Exception {
        server = new LocalTestServer(null, null);
        server.start();
        client = getDefaultClient();
    }

    @After
    public void tearDown() throws Exception {
        server.stop();
    }

    @Test
    public void testGetCoverageNotFound() {
        assertNull(client.getCoverage(TestUtils.TEST_SHA));
    }

    @Test
    public void testGetCoverageFound() {
        server.register("/*", TestUtils.makeHttpHandler(HttpStatus.SC_OK, "{}"));
        String coverage = client.getCoverage(TestUtils.TEST_SHA);
        assertEquals("{}", coverage);
    }

    @Test
    public void testGetCoverageInternalError() {
        server.register("/*", TestUtils.makeHttpHandler(HttpStatus.SC_INTERNAL_SERVER_ERROR, ""));
        assertNull(client.getCoverage(TestUtils.TEST_SHA));
    }

    @Test
    public void testGetParentCoverageNoBackend() {
        assertNull(client.getParentCoverage(TestUtils.TEST_SHA));
    }

    @Test
    public void testGetParentCoverageNullSHA() {
        assertNull(client.getParentCoverage(null));
    }

    @Test
    public void testGetParentCoverageNull() {
        server.register("/*", TestUtils.makeHttpHandler(HttpStatus.SC_OK, "null"));
        assertNull(client.getParentCoverage(TestUtils.TEST_SHA));
    }

    @Test
    public void testGetCoverageMissingKey() {
        server.register("/*", TestUtils.makeHttpHandler(HttpStatus.SC_OK, "{}"));
        assertNull(client.getParentCoverage(TestUtils.TEST_SHA));
    }

    @Test
    public void testGetCoverageWorkingBackend() throws IOException {
        JSONObject validJSON = TestUtils.getJSONFromFile(getClass(), "validCoverage");
        server.register("/*", TestUtils.makeHttpHandler(HttpStatus.SC_OK, validJSON.toString()));
        CodeCoverageMetrics metrics = client.getParentCoverage(TestUtils.TEST_SHA);
        assertEquals(42.0f, metrics.getLineCoveragePercent(), 0.01f);
    }

    @Test
    public void testRecordCoverageNullMetrics() {
        assertFalse(client.recordCoverage(TestUtils.TEST_SHA, null));
    }

    @Test
    public void testRecordCoverageInternalError() {
        server.register("/*", TestUtils.makeHttpHandler(HttpStatus.SC_INTERNAL_SERVER_ERROR, ""));
        assertFalse(client.recordCoverage(TestUtils.TEST_SHA, TestUtils.getDefaultCodeCoverageMetrics()));
    }

    @Test
    public void testRecordCoverageSuccessful() {
        server.register("/*", TestUtils.makeHttpHandler(HttpStatus.SC_OK, "{}"));
        assertTrue(client.recordCoverage(TestUtils.TEST_SHA, TestUtils.getDefaultCodeCoverageMetrics()));
    }

    @Test
    public void testRecordCoverageURISyntaxException() throws Exception {
        assertRecordCoverageException(URISyntaxException.class);
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
        HttpClient mockClient = mockClient();

        doThrow(exceptionClass).when(mockClient).executeMethod(any(HttpMethod.class));
        assertFalse(client.recordCoverage(TestUtils.TEST_SHA, TestUtils.getDefaultCodeCoverageMetrics()));
    }

    private void assertGetCoverageException(Class<? extends Exception> exceptionClass) throws IOException {
        HttpClient mockClient = mockClient();

        doThrow(exceptionClass).when(mockClient).executeMethod(any(HttpMethod.class));
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

    private HttpClient getMockHttpClient() {
        return mock(HttpClient.class);
    }

    private HttpClient mockClient() {
        HttpClient mockClient = getMockHttpClient();
        doReturn(mockClient).when(client).getClient();

        return mockClient;
    }

    private String getTestServerAddress() {
        return String.format(
                "http://%s:%s",
                server.getServiceAddress().getHostName(),
                server.getServiceAddress().getPort()
        );
    }
}
