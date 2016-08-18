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

package com.uber.jenkins.phabricator.credentials;

import com.cloudbees.plugins.credentials.CredentialsDescriptor;
import com.cloudbees.plugins.credentials.NameWith;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import com.uber.jenkins.phabricator.utils.CommonUtils;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.Extension;
import hudson.util.Secret;
import org.kohsuke.stapler.DataBoundConstructor;

@NameWith(value = ConduitCredentialsNameProvider.class, priority = 50)
@SuppressWarnings("unused")
public class ConduitCredentialsImpl extends BaseStandardCredentials implements ConduitCredentials {
    @NonNull
    private final Secret token;

    @Nullable
    private final String gateway;

    @NonNull
    private final String url;

    @DataBoundConstructor
    public ConduitCredentialsImpl(@CheckForNull String id,
                                  @NonNull @CheckForNull String url,
                                  @Nullable String gateway,
                                  @CheckForNull String description,
                                  @CheckForNull String token) {
        super(id, description);
        this.url = url;
        this.gateway = gateway;
        this.token = Secret.fromString(token);
    }

    @NonNull
    public String getUrl() {
        return url;
    }

    @Nullable
    public String getGateway() {
        return !CommonUtils.isBlank(gateway) ? gateway : getUrl();
    }

    @NonNull
    public Secret getToken() {
        return token;
    }

    @Extension
    @SuppressWarnings("unused")
    public static class Descriptor extends CredentialsDescriptor {
        /** {@inheritDoc} */
        @Override
        public String getDisplayName() {
            return "Phabricator Conduit Key";
        }
    }

}
