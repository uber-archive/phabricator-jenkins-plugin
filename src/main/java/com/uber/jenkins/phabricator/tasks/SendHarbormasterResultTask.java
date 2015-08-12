package com.uber.jenkins.phabricator.tasks;

import com.uber.jenkins.phabricator.conduit.ConduitAPIException;
import com.uber.jenkins.phabricator.conduit.DifferentialClient;
import com.uber.jenkins.phabricator.utils.Logger;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;

import java.io.IOException;

public class SendHarbormasterResultTask extends Task {

    private final DifferentialClient diffClient;
    private final String phid;
    private final boolean harbormasterSuccess;

    public SendHarbormasterResultTask(Logger logger, DifferentialClient diffClient, String phid, boolean harbormasterSuccess) {
        super(logger);
        this.diffClient = diffClient;
        this.phid = phid;
        this.harbormasterSuccess = harbormasterSuccess;
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
            JSONObject result = diffClient.sendHarbormasterMessage(phid, harbormasterSuccess);

            if (result.containsKey("error_info") && !(result.get("error_info") instanceof JSONNull)) {
                info(String.format("Error from Harbormaster: %s", result.getString("error_info")));
                this.result = Result.FAILURE;
            } else {
                this.result = Result.SUCCESS;
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
     * {@inheritDoc}
     */
    @Override
    protected void tearDown() {
        // Do nothing
    }
}
