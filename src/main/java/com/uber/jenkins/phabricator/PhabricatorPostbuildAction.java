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

import hudson.model.BuildBadgeAction;
import org.kohsuke.stapler.export.Exported;

/**
 * Ripped from https://github.com/jenkinsci/groovy-postbuild-plugin/blob/master/src/main/java/org/jvnet/hudson/plugins/groovypostbuild/GroovyPostbuildAction.java
 */
public class PhabricatorPostbuildAction implements BuildBadgeAction {
    private final String iconPath;
    private final String text;
    private String color = "#1FBAD6";
    private String background = "transparent";
    private String border = "0";
    private String borderColor = "transparent";
    private String link;

    private PhabricatorPostbuildAction(String iconPath, String text) {
        this.iconPath = iconPath;
        this.text = text;
    }

    public static PhabricatorPostbuildAction createBadge(String icon, String text) {
        return new PhabricatorPostbuildAction(PhabricatorPlugin.getIconPath(icon), text);
    }

    public static PhabricatorPostbuildAction createBadge(String icon, String text, String link) {
        PhabricatorPostbuildAction action = new PhabricatorPostbuildAction(PhabricatorPlugin.getIconPath(icon), text);
        action.link = link;
        return action;
    }

    public static PhabricatorPostbuildAction createShortText(String text) {
        return new PhabricatorPostbuildAction(null, text);
    }

    public static PhabricatorPostbuildAction createInfoBadge(String text) {
        return new PhabricatorPostbuildAction(PhabricatorPlugin.getIconPath("info.gif"), text);
    }

    public static PhabricatorPostbuildAction createWarningBadge(String text) {
        return new PhabricatorPostbuildAction(PhabricatorPlugin.getIconPath("warning.gif"), text);
    }

    public static PhabricatorPostbuildAction createErrorBadge(String text) {
        return new PhabricatorPostbuildAction(PhabricatorPlugin.getIconPath("error.gif"), text);
    }

    /* Action methods */
    public String getUrlName() { return ""; }
    public String getDisplayName() { return ""; }
    public String getIconFileName() { return null; }

    @Exported public boolean isTextOnly() { return (iconPath == null); }
    @Exported public String getIconPath() { return iconPath; }
    @Exported public String getText() { return text; }
    @Exported public String getColor() { return color; }
    @Exported public String getBackground() { return background; }
    @Exported public String getBorder() { return border; }
    @Exported public String getBorderColor() { return borderColor; }
    @Exported public String getLink() { return link; }

}
