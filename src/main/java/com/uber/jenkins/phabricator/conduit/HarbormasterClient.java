package com.uber.jenkins.phabricator.conduit;

import com.uber.jenkins.phabricator.unit.UnitResults;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HarbormasterClient {
    private final ConduitAPIClient conduit;

    public HarbormasterClient(ConduitAPIClient conduit) {
        this.conduit = conduit;
    }
    /**
     * Sets a sendHarbormasterMessage build status
     * @param phid Phabricator object ID
     * @param pass whether or not the build passed
     * @param unitResults the results from the unit tests
     * @param coverage the results from the coverage provider
     * @return the Conduit API response
     * @throws IOException if there is a network error talking to Conduit
     * @throws ConduitAPIException if any error is experienced talking to Conduit
     */
    public JSONObject sendHarbormasterMessage(String phid, boolean pass, UnitResults unitResults, Map<String, String> coverage) throws ConduitAPIException, IOException {

        List<JSONObject> unit = new ArrayList<JSONObject>();

        if (unitResults != null) {
            unit.addAll(unitResults.toHarbormaster());
        }

        if (coverage != null) {
            JSONObject coverageUnit = new JSONObject()
                    .element("result", "pass")
                    .element("name", "Coverage Data")
                    .element("coverage", coverage);
            unit.add(coverageUnit);
        }

        JSONObject params = new JSONObject();
        params.element("type", pass ? "pass" : "fail")
                .element("buildTargetPHID", phid);

        if (!unit.isEmpty()) {
            params.element("unit", unit);
        }

        return conduit.perform("harbormaster.sendmessage", params);
    }

    /**
     * Uploads a uri as an 'artifact' for Harbormaster to display
     * @param phid Phabricator object ID
     * @param buildUri Uri to display, presumably the jenkins builds
     * @return the Conduit API response
     * @throws IOException if there is a network error talking to Conduit
     * @throws ConduitAPIException if any error is experienced talking to Conduit
     */
    public JSONObject sendHarbormasterUri(String phid, String buildUri) throws ConduitAPIException, IOException {
        JSONObject artifactData = new JSONObject();
        artifactData = artifactData.element("uri", buildUri)
                .element("name", "Jenkins")
                .element("ui.external", true);

        JSONObject params = new JSONObject();
        params.element("buildTargetPHID", phid)
                .element("artifactKey", "jenkins.uri")
                .element("artifactType", "uri")
                .element("artifactData", artifactData);

        return conduit.perform("harbormaster.createartifact", params);
    }
}
