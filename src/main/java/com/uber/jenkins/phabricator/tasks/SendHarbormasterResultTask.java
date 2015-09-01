package com.uber.jenkins.phabricator.tasks;

import com.uber.jenkins.phabricator.conduit.ConduitAPIException;
import com.uber.jenkins.phabricator.conduit.DifferentialClient;
import com.uber.jenkins.phabricator.utils.Logger;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.util.Map;

public class SendHarbormasterResultTask extends Task {

    private final DifferentialClient diffClient;
    private final String phid;
    private final boolean harbormasterSuccess;
    private final Map<String, String> coverage;

    public SendHarbormasterResultTask(Logger logger, DifferentialClient diffClient, String phid, boolean harbormasterSuccess, Map<String, String> harbormasterCoverage) {
        super(logger);
        this.diffClient = diffClient;
        this.phid = phid;
        this.harbormasterSuccess = harbormasterSuccess;
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
            if (!sendMessage(coverage)) {
                info("Error sending Harbormaster unit results, trying again without unit data (you may have an old Phabricator?).");
                sendMessage(null);
            }
        } catch (ConduitAPIException e) {
            e.printStackTrace();
            this.result = Result.FAILURE;
        } catch (IOException e) {
            e.printStackTrace();
            this.result = Result.FAILURE;
        }
    }

    /**
     * Try to send a message to harbormaster
     * @return false if an error was encountered
     */
    private boolean sendMessage( Map<String, String> coverage) throws IOException, ConduitAPIException {
        JSONObject result = diffClient.sendHarbormasterMessage(phid, harbormasterSuccess, coverage);

        if (result.containsKey("error_info") && !(result.get("error_info") instanceof JSONNull)) {
            info(String.format("Error from Harbormaster: %s", result.getString("error_info")));
            this.result = Result.FAILURE;
            return false;
        } else {
            this.result = Result.SUCCESS;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void tearDown() {
        // Do nothing
    }
}
