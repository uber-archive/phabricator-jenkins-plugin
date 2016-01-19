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

public class InstanceProvider<T> {
    private static final String LOGGER_TAG = "plugin-provider";
    private final Provider<T> provider;
    private final String className;
    private final Logger logger;
    private final String pluginName;

    /**
     * Encapsulate lazilly loading a concrete implementation when a plugin is available
     * @param jenkins the instance of Jenkins
     * @param pluginName the name of the plugin, e.g. "cobertura" or "junit" (maven name)
     * @param className the concrete class name (com.uber.phabricator...)
     * @param logger the logger to use
     */
    public InstanceProvider(Jenkins jenkins, String pluginName, String className, Logger logger) {
        this.provider = new BaseProvider<T>(
                jenkins,
                pluginName,
                logger
        );
        this.pluginName = pluginName;
        this.className = className;
        this.logger = logger;
    }

    /**
     * Get an instance of the desired implementation, if available
     * @return the class desired
     */
    public T getInstance() {
        if (!provider.isAvailable()) {
            logger.info(LOGGER_TAG, String.format("'%s' plugin not installed.", pluginName));
            return null;
        }
        T instance = provider.getInstance(className);
        if (instance == null) {
            logger.warn(LOGGER_TAG,
                    String.format("Unable to instantiate plugin provider for '%s'. This should not happen.", pluginName));
        }
        return instance;
    }
}
