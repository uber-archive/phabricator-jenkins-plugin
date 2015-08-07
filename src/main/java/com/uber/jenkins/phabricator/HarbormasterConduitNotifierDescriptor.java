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

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Publisher;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.StaplerRequest;

@Extension
public class HarbormasterConduitNotifierDescriptor extends BuildStepDescriptor<Publisher> {
    private String conduitURL;
    private String conduitToken;
    private boolean enabled = true;

    public HarbormasterConduitNotifierDescriptor() {
        super(HarbormasterConduitNotifier.class);
        load();
    }

    public boolean isApplicable(Class<? extends AbstractProject> aClass) {
        return getEnabled();
    }

    public String getDisplayName() {
        return "Callback to Harbormaster";
    }

    @Override
    public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
        if (formData.containsKey("harbormaster-conduit-notifier")) {
            req.bindJSON(this, formData.getJSONObject("harbormaster-conduit-notifier"));
            setEnabled(true);
        } else {
            setEnabled(false);
        }
        save();
        return super.configure(req, formData);
    }

    public boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getConduitURL() {
        if (conduitURL != null && !"".equals(conduitURL)) {
            return conduitURL;
        }
        return null;
    }

    public void setConduitURL(String value) {
        conduitURL = value;
    }

    public String getConduitToken() {
        return conduitToken;
    }

    public void setConduitToken(String conduitToken) {
        this.conduitToken = conduitToken;
    }
}
