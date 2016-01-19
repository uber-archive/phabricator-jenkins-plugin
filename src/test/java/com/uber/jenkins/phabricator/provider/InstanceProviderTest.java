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

package com.uber.jenkins.phabricator.provider;

import com.uber.jenkins.phabricator.unit.UnitTestProvider;
import com.uber.jenkins.phabricator.utils.TestUtils;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertNull;

public class InstanceProviderTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testUnavailablePlugin() {
        InstanceProvider<UnitTestProvider> provider = makeProvider("weird-plugin-name", "anything");
        assertNull(provider.getInstance());
    }

    @Test
    public void testUnavailablePluginValidClass() {
        InstanceProvider<UnitTestProvider> provider = makeProvider("weird-plugin-name", "com.uber.jenkins.phabricator.unit.JUnitTestProvider");
        assertNull(provider.getInstance());
    }

    @Test
    public void testBadClassName() {
        InstanceProvider<UnitTestProvider> provider = makeProvider("junit", "com.nonexistent.class");
        assertNull(provider.getInstance());
    }

    private InstanceProvider<UnitTestProvider> makeProvider(String pluginName, String className) {
        return new InstanceProvider<UnitTestProvider>(
                j.getInstance(),
                pluginName,
                className,
                TestUtils.getDefaultLogger()
        );
    }
}
