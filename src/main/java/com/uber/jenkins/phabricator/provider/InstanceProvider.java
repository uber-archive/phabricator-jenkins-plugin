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

import com.uber.jenkins.phabricator.coverage.CoberturaCoverageProvider;
import com.uber.jenkins.phabricator.coverage.CoverageProvider;
import com.uber.jenkins.phabricator.coverage.JacocoCoverageProvider;
import com.uber.jenkins.phabricator.unit.JUnitTestProvider;
import com.uber.jenkins.phabricator.unit.UnitTestProvider;
import com.uber.jenkins.phabricator.utils.Logger;

import java.util.Map;
import java.util.Set;

import hudson.FilePath;
import hudson.model.Run;
import jenkins.model.Jenkins;

public abstract class InstanceProvider<T> {

    private static final String COBERTURA_PLUGIN_NAME = "cobertura";
    private static final String JACOCO_PLUGIN_NAME = "jacoco";
    private static final String JUNIT_PLUGIN_NAME = "junit";

    private static final String LOGGER_TAG = "plugin-provider";
    private final Jenkins jenkins;
    private final String pluginName;
    private final Logger logger;

    /**
     * Encapsulate lazily loading a concrete implementation when a plugin is available
     *
     * @param jenkins the instance of Jenkins
     * @param pluginName the name of the plugin, e.g. "cobertura" or "junit" (maven name)
     */
    private InstanceProvider(Jenkins jenkins, String pluginName, Logger logger) {
        this.jenkins = jenkins;
        this.pluginName = pluginName;
        this.logger = logger;
    }

    /**
     * Get an instance of the desired implementation, if available
     *
     * @return the class desired
     */
    final T getInstance() {
        if (jenkins.getPlugin(pluginName) == null) {
            logger.info(LOGGER_TAG, String.format("'%s' plugin not installed.", pluginName));
            return null;
        }
        return makeInstance();
    }

    abstract T makeInstance();

    public static CoverageProvider getCoberturaCoverageProvider(
            final Run<?, ?> build,
            final Set<String> includeFiles, final String coverageReportPattern, Logger logger) {
        return new InstanceProvider<CoverageProvider>(Jenkins.getInstance(),
                COBERTURA_PLUGIN_NAME, logger) {
            @Override
            protected CoverageProvider makeInstance() {
                return new CoberturaCoverageProvider(build, includeFiles, coverageReportPattern);
            }
        }.getInstance();
    }

    public static CoverageProvider getJacocoCoverageProvider(
            final Run<?, ?> build,
            final Set<String> includeFiles, final String coverageReportPattern, Logger logger) {
        return new InstanceProvider<CoverageProvider>(Jenkins.getInstance(),
                JACOCO_PLUGIN_NAME, logger) {
            @Override
            protected CoverageProvider makeInstance() {
                return new JacocoCoverageProvider(build, includeFiles, coverageReportPattern);
            }
        }.getInstance();
    }

    public static UnitTestProvider getUnitTestProvider(Logger logger) {
        return new InstanceProvider<UnitTestProvider>(Jenkins.getInstance(),
                JUNIT_PLUGIN_NAME, logger) {
            @Override
            protected UnitTestProvider makeInstance() {
                return new JUnitTestProvider();
            }
        }.getInstance();
    }
}
