package com.uber.jenkins.phabricator;

import jenkins.model.CauseOfInterruption;

public final class PhabricatorCauseOfInterruption extends CauseOfInterruption {
    private final String buildUrl;

    PhabricatorCauseOfInterruption(String buildUrl) {
        this.buildUrl = buildUrl;
    }

    @Override
    public String getShortDescription() {
        return String.format("Aborted by %s", buildUrl);
    }
}
