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

import hudson.model.Action;
import org.apache.commons.lang.StringEscapeUtils;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Ripped from https://github.com/jenkinsci/groovy-postbuild-plugin/blob/master/src/main/java/org/jvnet/hudson/plugins/groovypostbuild/GroovyPostbuildSummaryAction.java
 */
@ExportedBean(defaultVisibility=2)
public class PhabricatorPostbuildSummaryAction implements Action {
    private final String iconPath;
    private final StringBuilder textBuilder = new StringBuilder();

    public PhabricatorPostbuildSummaryAction(String iconPath) {
        this.iconPath = PhabricatorPlugin.getIconPath(iconPath);
    }

    /* Action methods */
    public String getUrlName() { return ""; }
    public String getDisplayName() { return ""; }
    public String getIconFileName() { return null; }

    @Exported public String getIconPath() { return iconPath; }
    @Exported public String getText() { return textBuilder.toString(); }

    public void appendText(String text) {
        if(false) {
            text = StringEscapeUtils.escapeHtml(text);
        }
        textBuilder.append(text);
    }
}