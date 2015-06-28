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

import com.uber.jenkins.phabricator.utils.CommonUtils;
import hudson.Launcher;
import hudson.util.ArgumentListBuilder;
import net.sf.json.JSONObject;
import net.sf.json.groovy.JsonSlurper;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Map;

public class ArcanistClient {
    private final String arcPath;
    private final String methodName;
    private final Map<String, String> params;
    private final String conduitToken;
    private final String[] arguments;

    public ArcanistClient(String arcPath, String methodName, Map<String, String> params, String conduitToken, String... arguments) {
        this.arcPath = arcPath;
        this.methodName = methodName;
        this.params = params;
        this.conduitToken = conduitToken;
        this.arguments = arguments;
    }

    private ArgumentListBuilder getConduitCommand() {
        ArgumentListBuilder builder = new ArgumentListBuilder(this.arcPath, this.methodName);
        builder.add(arguments);

        if (!CommonUtils.isBlank(this.conduitToken)) {
            builder.addMasked("--conduit-token=" + this.conduitToken);
        }
        return builder;
    }

    private Launcher.ProcStarter getCommand(Launcher.ProcStarter starter) throws IOException {
        Launcher.ProcStarter command = starter.cmds(this.getConduitCommand());

        if (this.params != null) {
            JSONObject obj = new JSONObject();
            obj.putAll(this.params);

            InputStream jsonStream = IOUtils.toInputStream(obj.toString(), "UTF-8");
            command = command.stdin(jsonStream);
        }
        return command;
    }

    public int callConduit(Launcher.ProcStarter starter, PrintStream stderr) throws IOException, InterruptedException {
        Launcher.ProcStarter command = this.getCommand(starter);
        return command.stdout(stderr).stderr(stderr).join();
    }

    public JSONObject parseConduit(Launcher.ProcStarter starter, PrintStream stderr)
            throws ArcanistUsageException, IOException, InterruptedException {
        Launcher.ProcStarter command = this.getCommand(starter);
        ByteArrayOutputStream stdoutBuffer = new ByteArrayOutputStream();

        int returnCode = command.
                stdout(stdoutBuffer).
                stderr(stderr).
                join();

        if (returnCode != 0) {
            String errorMessage = stdoutBuffer.toString();
            stderr.println("[arcanist] returned non-zero exit code " + returnCode);
            stderr.println("[arcanist] output: " + errorMessage);
            throw new ArcanistUsageException(errorMessage);
        }

        JsonSlurper jsonParser = new JsonSlurper();
        try {
            return (JSONObject) jsonParser.parseText(stdoutBuffer.toString());
        } catch(net.sf.json.JSONException e) {
            stderr.println("[arcanist] Unable to parse JSON from response: " + stdoutBuffer.toString());
            throw e;
        }
    }
}
