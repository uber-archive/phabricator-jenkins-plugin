package com.uber.jenkins.phabricator.tasks;

import com.uber.jenkins.phabricator.conduit.ConduitAPIException;
import com.uber.jenkins.phabricator.conduit.DifferentialClient;
import com.uber.jenkins.phabricator.utils.TestUtils;
import net.sf.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

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
        when(diffClient.sendHarbormasterMessage(TestUtils.TEST_PHID, false)).thenReturn(validResponse);

        assertEquals(Task.Result.SUCCESS, getResult());
    }

    @Test
    public void testErrorInfoResponse() throws IOException, ConduitAPIException {
        JSONObject errorResponse = new JSONObject();
        errorResponse.put("error_info", "i'm having a bad day");
        when(diffClient.sendHarbormasterMessage(TestUtils.TEST_PHID, false)).thenReturn(errorResponse);

        assertEquals(Task.Result.FAILURE, getResult());
    }

    @Test
    public void testConduitAPIFailure() throws IOException, ConduitAPIException {
        when(diffClient.sendHarbormasterMessage(TestUtils.TEST_PHID, false)).thenThrow(ConduitAPIException.class);

        assertEquals(Task.Result.FAILURE, getResult());
    }

    @Test
    public void testIOExceptionFailure() throws IOException, ConduitAPIException {
        when(diffClient.sendHarbormasterMessage(TestUtils.TEST_PHID, false)).thenThrow(IOException.class);

        assertEquals(Task.Result.FAILURE, getResult());
    }

    private Task.Result getResult() {
        return new SendHarbormasterResultTask(
                TestUtils.getDefaultLogger(),
                diffClient,
                TestUtils.TEST_PHID,
                false
        ).run();
    }
}
