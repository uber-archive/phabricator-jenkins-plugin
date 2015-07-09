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

import hudson.Plugin;
import hudson.PluginWrapper;
import jenkins.model.Jenkins;

import java.io.File;

public class PhabricatorPlugin extends Plugin {
    public static final String CONDUIT_TOKEN = "CONDUIT_TOKEN";
    public static final String ARCANIST_PATH = "ARCANIST_PATH";

    // Diff ID (not differential ID)
    static final String DIFFERENTIAL_ID_FIELD = "DIFF_ID";
    // Phabricator object ID (for Harbormaster)
    static final String PHID_FIELD = "PHID";

    static final String WRAP_KEY = "PHABRICATOR_JENKINS";

    public static String getIconPath(String icon) {
        if (icon == null) {
            return null;
        }
        if (icon.startsWith("/")) {
            return icon;
        }

        // Try plugin images dir, fallback to Hudson images dir
        PluginWrapper wrapper = Jenkins.getInstance().getPluginManager().getPlugin(PhabricatorPlugin.class);

        boolean pluginIconExists = (wrapper != null) && new File(wrapper.baseResourceURL.getPath() + "/images/" + icon).exists();
        return pluginIconExists ? "/plugin/" + wrapper.getShortName() + "/images/" + icon : Jenkins.RESOURCE_PATH + "/images/16x16/" + icon;
    }
}
