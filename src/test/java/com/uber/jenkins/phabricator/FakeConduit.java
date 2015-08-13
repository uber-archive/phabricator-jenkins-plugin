// Copyright (c) 2015 Uber
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

package com.uber.jenkins.phabricator;

import com.uber.jenkins.phabricator.utils.TestUtils;
import net.sf.json.JSONObject;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.http.localserver.LocalTestServer;

import java.util.Map;

public class FakeConduit {
    private LocalTestServer server;

    public FakeConduit(Map<String, JSONObject> responses) throws Exception {
        server = new LocalTestServer(null, null);
        for (Map.Entry<String, JSONObject> entry : responses.entrySet()) {
            register(entry.getKey(), entry.getValue());
        }
        server.start();
    }

    public void stop() throws Exception {
        server.stop();
    }

    public String URI() {
        return TestUtils.getTestServerAddress(server);
    }

    public void register(String method, JSONObject response) {
        server.register(
                "/api/" + method,
                TestUtils.makeHttpHandler(HttpStatus.SC_OK, response.toString(2))
        );
    }
}
