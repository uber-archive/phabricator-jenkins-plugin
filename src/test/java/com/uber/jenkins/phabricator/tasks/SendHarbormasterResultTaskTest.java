package com.uber.jenkins.phabricator.tasks;

import com.uber.jenkins.phabricator.conduit.ConduitAPIException;
import com.uber.jenkins.phabricator.conduit.DifferentialClient;
import com.uber.jenkins.phabricator.conduit.HarbormasterClient.MessageType;
import com.uber.jenkins.phabricator.utils.TestUtils;

import net.sf.json.JSONObject;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SendHarbormasterResultTaskTest {

    private final JSONObject validResponse = new JSONObject();
    private DifferentialClient diffClient;

    @Before
    public void setUp() {
        diffClient = mock(DifferentialClient.class);
    }

    @Test
    public void testSuccessfulHarbormaster() throws IOException, ConduitAPIException {
        when(diffClient.sendHarbormasterMessage(TestUtils.TEST_PHID, MessageType.fail, null, null, null)).thenReturn(
                validResponse);

        assertEquals(Task.Result.SUCCESS, getResult());
    }

    @Test
    public void testErrorInfoResponse() throws IOException, ConduitAPIException {
        when(diffClient.sendHarbormasterMessage(TestUtils.TEST_PHID, MessageType.fail, null, null, null)).thenReturn(
                getErrorResponse());

        assertEquals(Task.Result.FAILURE, getResult());
    }

    @Test
    public void testRetryOnUnitError() throws Exception {
        Map<String, String> coverage = new HashMap<String, String>();
        coverage.put("filename", "NNNUC");
        when(diffClient.sendHarbormasterMessage(TestUtils.TEST_PHID, MessageType.fail, null, coverage, null)).thenReturn(
                getErrorResponse());
        when(diffClient.sendHarbormasterMessage(TestUtils.TEST_PHID, MessageType.fail, null, null, null)).thenReturn(
                validResponse);

        assertEquals(Task.Result.SUCCESS, getResult(coverage));
    }

    @Test
    public void testConduitAPIFailure() throws IOException, ConduitAPIException {
        when(diffClient.sendHarbormasterMessage(TestUtils.TEST_PHID, MessageType.fail, null, null, null)).thenThrow(
                ConduitAPIException.class);

        assertEquals(Task.Result.FAILURE, getResult());
    }

    @Test
    public void testIOExceptionFailure() throws IOException, ConduitAPIException {
        when(diffClient.sendHarbormasterMessage(TestUtils.TEST_PHID, MessageType.fail, null, null, null)).thenThrow(
                IOException.class);

        assertEquals(Task.Result.FAILURE, getResult());
    }

    private Task.Result getResult(Map<String, String> coverage) {
        return new SendHarbormasterResultTask(
                TestUtils.getDefaultLogger(),
                diffClient,
                TestUtils.TEST_PHID,
                MessageType.fail,
                null,
                coverage,
                null
        ).run();
    }

    private Task.Result getResult() {
        return getResult(null);
    }

    private JSONObject getErrorResponse() {
        return new JSONObject().element("error_info", "i'm having a bad day");
    }
}
