package com.uber.jenkins.phabricator.tasks;

import com.uber.jenkins.phabricator.conduit.ConduitAPIException;
import com.uber.jenkins.phabricator.conduit.DifferentialClient;
import com.uber.jenkins.phabricator.unit.UnitResults;
import com.uber.jenkins.phabricator.utils.Logger;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.util.Map;

public class SendHarbormasterResultTask extends Task {

    private final DifferentialClient diffClient;
    private final String phid;
    private final boolean harbormasterSuccess;
    private UnitResults unitResults;
    private final Map<String, String> coverage;

    public SendHarbormasterResultTask(Logger logger, DifferentialClient diffClient, String phid,
                                      boolean harbormasterSuccess, UnitResults unitResults,
                                      Map<String, String> harbormasterCoverage) {
        super(logger);
        this.diffClient = diffClient;
        this.phid = phid;
        this.harbormasterSuccess = harbormasterSuccess;
        this.unitResults = unitResults;
        this.coverage = harbormasterCoverage;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getTag() {
        return "send-harbormaster-result";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setup() {
        // Do nothing
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void execute() {
        try {
            if (!sendMessage(unitResults, coverage)) {
                info("Error sending Harbormaster unit results, trying again without unit data (you may have an old Phabricator?).");
                sendMessage(null, null);
            }
        } catch (ConduitAPIException e) {
            e.printStackTrace();
            failTask();
        } catch (IOException e) {
            e.printStackTrace();
            failTask();
        }
    }

    /**
     * Try to send a message to harbormaster
     * @return false if an error was encountered
     */
    private boolean sendMessage(UnitResults unitResults, Map<String, String> coverage) throws IOException, ConduitAPIException {
        JSONObject result = diffClient.sendHarbormasterMessage(phid, harbormasterSuccess, unitResults, coverage);

        if (result.containsKey("error_info") && !(result.get("error_info") instanceof JSONNull)) {
            info(String.format("Error from Harbormaster: %s", result.getString("error_info")));
            failTask();
            return false;
        } else {
            this.result = Result.SUCCESS;
        }
        return true;
    }

    private void failTask() {
        info("Unable to post to Harbormaster");
        result = result.FAILURE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void tearDown() {
        // Do nothing
    }
}
