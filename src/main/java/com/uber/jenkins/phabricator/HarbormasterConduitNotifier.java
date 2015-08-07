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

import com.uber.jenkins.phabricator.utils.Logger;
import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Notifier;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class HarbormasterConduitNotifier extends Notifier {
    private static final String TAG = "harbormaster-conduit";

    private final String buildTarget;

    @DataBoundConstructor
    public HarbormasterConduitNotifier(String buildTarget) {
        this.buildTarget = buildTarget;
    }

    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public final boolean perform(final AbstractBuild<?, ?> build, final Launcher launcher,
                                 final BuildListener listener) throws InterruptedException, IOException {
        String harbormasterResult = "fail";
        EnvVars environment = build.getEnvironment(listener);
        Logger log = new Logger(listener.getLogger());
        Result buildResult = build.getResult();

        // TODO(cburroughs): make this more sophisticated, or configurable
        if (buildResult.isBetterOrEqualTo(hudson.model.Result.UNSTABLE)) {
            harbormasterResult = "pass";
        }
        log.info(TAG, "Translated harbormaster build result: " + harbormasterResult);
        String buildTargetPHID = environment.get(buildTarget);
        if (buildTargetPHID == null) {
            log.info(TAG, "Unable to find value for paramter: " + buildTarget);
            return false;
        }
        harbormasterSendMessage(log, getDescriptor().getConduitURL(), getDescriptor().getConduitToken(), buildTargetPHID, harbormasterResult);
        return true;
    }

    private void harbormasterSendMessage(Logger log, String conduitURL, String conduitToken, String buildTargetPHID, String harbormasterResult) {
        try {
            CloseableHttpClient client = HttpClientBuilder.create().build();
            HttpPost httppost = new HttpPost(conduitURL + "/harbormaster.sendmessage");
            List<NameValuePair> formparams = new ArrayList<NameValuePair>();
            formparams.add(new BasicNameValuePair("api.token", conduitToken));
            formparams.add(new BasicNameValuePair("buildTargetPHID", buildTargetPHID));
            formparams.add(new BasicNameValuePair("type", harbormasterResult));
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(formparams);
            httppost.setEntity(entity);
            HttpResponse response = client.execute(httppost);

            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                log.info(TAG, "Call failed: " + response.getStatusLine());
            }
        } catch (HttpResponseException e) {
            if (e.getStatusCode() != 404) {
                e.printStackTrace(log.getStream());
            }
        } catch (ClientProtocolException e) {
            e.printStackTrace(log.getStream());
        } catch (IOException e) {
            e.printStackTrace(log.getStream());
        }
    }

    @Override
    public HarbormasterConduitNotifierDescriptor getDescriptor() {
        return (HarbormasterConduitNotifierDescriptor) super.getDescriptor();
    }
}
