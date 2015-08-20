package com.uber.jenkins.phabricator.uberalls;

import com.uber.jenkins.phabricator.coverage.CodeCoverageMetrics;
import com.uber.jenkins.phabricator.utils.TestUtils;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.http.localserver.LocalTestServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

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

    private UberallsClient getDefaultClient() {
        return new UberallsClient(
                getTestServerAddress(),
                TestUtils.getDefaultLogger(),
                TestUtils.TEST_REPOSITORY,
                TestUtils.TEST_BRANCH
        );
    }

    private String getTestServerAddress() {
        return String.format(
                "http://%s:%s",
                server.getServiceAddress().getHostName(),
                server.getServiceAddress().getPort()
        );
    }
}
