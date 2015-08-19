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

import com.uber.jenkins.phabricator.utils.Logger;
import jenkins.model.Jenkins;

public abstract class Provider<T> {
    protected final Logger logger;
    private final Jenkins jenkins;
    private final String pluginName;

    public Provider(final Jenkins jenkins, final String pluginName, final Logger logger) {
        this.jenkins = jenkins;
        this.pluginName = pluginName;
        this.logger = logger;
    }

    /**
     * Determine if the provider is available for the plugin
     * @return Whether the plugin is available
     */
    public boolean isAvailable() {
        return jenkins.getPlugin(pluginName) != null;
    }

    public abstract T getInstance(final String implementationName);
}
