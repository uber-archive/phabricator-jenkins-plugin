// Copyright (c) 2015 Uber Technologies, Inc.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

package com.uber.jenkins.phabricator;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import hudson.model.Action;

/**
 * Ripped from https://github.com/jenkinsci/groovy-postbuild-plugin/blob/master/src/main/java/org/jvnet/hudson/plugins/groovypostbuild/GroovyPostbuildSummaryAction.java
 */
@ExportedBean(defaultVisibility = 2)
public class PhabricatorPostbuildSummaryAction implements Action {

    private final String iconPath;
    private final String url;
    private final String diffID;
    private final String revisionID;
    private final String authorName;
    private final String authorEmail;
    private final String commitMessage;

    public PhabricatorPostbuildSummaryAction(
            String iconPath, String phabricatorLink, String diffID, String revisionID,
            String authorName, String authorEmail, String commitMessage) {
        this.iconPath = iconPath;
        this.url = phabricatorLink;
        this.diffID = diffID;
        this.revisionID = revisionID;
        this.authorName = authorName;
        this.authorEmail = authorEmail;
        this.commitMessage = commitMessage;
    }

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return "";
    }

    /* Action methods */
    public String getUrlName() {
        return "";
    }

    @Exported
    public String getIconPath() {
        return PhabricatorPlugin.getIconPath(iconPath);
    }

    @Exported
    public String getUrl() {
        return url;
    }

    @Exported
    public String getRevisionID() {
        return revisionID;
    }

    @Exported
    public String getDiffID() {
        return diffID;
    }

    @Exported
    public String getAuthorName() {
        return authorName;
    }

    @Exported
    public String getAuthorEmail() {
        return authorEmail;
    }

    @Exported
    public String getCommitMessage() {
        return commitMessage;
    }
}
