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

package com.uber.jenkins.phabricator.conduit;

import com.uber.jenkins.phabricator.CommonUtils;
import hudson.Launcher;
import net.sf.json.JSONObject;
import net.sf.json.groovy.JsonSlurper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ArcanistClient {
    private final String methodName;
    private final Map<String, String> params;
    private final String conduitToken;

    public ArcanistClient(String methodName, Map<String, String> params, String conduitToken) {
        this.methodName = methodName;
        this.params = params;
        this.conduitToken = conduitToken;
    }

    private String getConduitCommand(String methodName) {
        StringBuilder sb = new StringBuilder("arc call-conduit ");
        sb.append(methodName);
        if (!CommonUtils.isBlank(this.conduitToken)) {
            sb.append(" --conduit-token=");
            sb.append(this.conduitToken);
        }
        return sb.toString();
    }

    public JSONObject callConduit(Launcher.ProcStarter starter, PrintStream stderr) throws IOException, InterruptedException {
        JSONObject obj = new JSONObject();
        obj.putAll(this.params);
        List<String> command = Arrays.asList(
                "sh", "-c", "echo '" + obj.toString() + "' | " + this.getConduitCommand(this.methodName));
        ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();

        // TODO handle bad return code
        starter.cmds(command).stdout(stdoutBuffer).stderr(stderr).join();
        JsonSlurper jsonParser = new JsonSlurper();
        try {
            return (JSONObject) jsonParser.parseText(stdoutBuffer.toString());
        } catch(net.sf.json.JSONException e) {
            stderr.println("[phabricator] Unable to parse JSON from response: " + stdoutBuffer.toString());
            throw e;
        }
    }
}
