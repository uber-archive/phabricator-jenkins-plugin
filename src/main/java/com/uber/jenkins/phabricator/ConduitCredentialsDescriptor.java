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

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.uber.jenkins.phabricator.credentials.ConduitCredentials;
import hudson.model.Item;
import hudson.model.Job;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;

import java.util.ArrayList;
import java.util.List;

public class ConduitCredentialsDescriptor {
    public static List<ConduitCredentials> availableCredentials(Job owner) {
        return CredentialsProvider.lookupCredentials(
                ConduitCredentials.class,
                owner,
                null,
                new ArrayList<DomainRequirement>()
        );
    }

    public static ConduitCredentials getCredentials(Job owner, String credentialsID) {
        List<ConduitCredentials> available = availableCredentials(owner);
        if (available.size() == 0) {
            return null;
        }
        return CredentialsMatchers.firstOrDefault(
                available,
                CredentialsMatchers.allOf(CredentialsMatchers.withId(credentialsID)),
                available.get(0)
        );
    }

    public static ListBoxModel doFillCredentialsIDItems(@AncestorInPath Jenkins context) {
        if (context == null || !context.hasPermission(Item.CONFIGURE)) {
            return new StandardListBoxModel();
        }

        List<DomainRequirement> domainRequirements = new ArrayList<DomainRequirement>();
        return new StandardListBoxModel()
                .withEmptySelection()
                .withMatching(
                        CredentialsMatchers.anyOf(
                                CredentialsMatchers.instanceOf(ConduitCredentials.class)),
                        CredentialsProvider.lookupCredentials(
                                StandardCredentials.class,
                                context,
                                ACL.SYSTEM,
                                domainRequirements));
    }
}
