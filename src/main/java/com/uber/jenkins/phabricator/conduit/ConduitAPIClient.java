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

import net.sf.json.JSONObject;
import net.sf.json.groovy.JsonSlurper;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ConduitAPIClient {
    private static final String API_TOKEN_KEY = "api.token";

    private final String conduitURL;
    private final String conduitToken;

    public ConduitAPIClient(String conduitURL, String conduitToken) {
        this.conduitURL = conduitURL;
        this.conduitToken = conduitToken;
    }

    /**
     * Call the conduit API of Phabricator
     * @param action Name of the API call
     * @param data The data to send to Harbormaster
     * @return The result as a JSONObject
     * @throws IOException If there was a problem reading the response
     * @throws ConduitAPIException If there was an error calling conduit
     */
    public JSONObject perform(String action, Map<String, String> data) throws IOException, ConduitAPIException {
        CloseableHttpClient client = HttpClientBuilder.create().build();
        HttpUriRequest request = createRequest(action, data);

        HttpResponse response;
        try {
            response = client.execute(request);
        } catch (ClientProtocolException e) {
            throw new ConduitAPIException(e.getMessage());
        }

        InputStream responseBody = response.getEntity().getContent();

        if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            throw new ConduitAPIException(responseBody.toString(), response.getStatusLine().getStatusCode());
        }

        JsonSlurper jsonParser = new JsonSlurper();
        return (JSONObject)jsonParser.parse(responseBody);
    }

    public HttpUriRequest createRequest(String action, Map<String, String> data) throws UnsupportedEncodingException, ConduitAPIException {
        HttpPost post;
        try {
            post = new HttpPost(
                    new URL(new URL(new URL(conduitURL), "/api/"), action).toURI()
            );
        } catch (MalformedURLException e) {
            throw new ConduitAPIException(e.getMessage());
        } catch (URISyntaxException e) {
            throw new ConduitAPIException(e.getMessage());
        }

        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair(API_TOKEN_KEY, conduitToken));
        for (Map.Entry<String, String> datum : data.entrySet()) {
            params.add(new BasicNameValuePair(datum.getKey(), datum.getValue()));
        }

        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params);
        post.setEntity(entity);

        return post;
    }
}
