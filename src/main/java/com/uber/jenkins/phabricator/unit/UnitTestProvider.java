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

package com.uber.jenkins.phabricator.unit;

import hudson.model.AbstractBuild;

/**
 * Provides an interface for plugins that have unit test results
 */
public abstract class UnitTestProvider {
    private AbstractBuild<?, ?> build;

    /**
     * Set the owning build for this provider
     * @param build The build that is associated with the current run
     */
    public void setBuild(AbstractBuild<?, ?> build) {
        this.build = build;
    }

    protected AbstractBuild getBuild() {
        return build;
    }

    /**
     * Determine if the current provider has results available for the build
     * @return Whether results are available
     */
    public abstract boolean resultsAvailable();

    /**
     * Convert the provider's results to a standard format
     * @return The results of the unit tests
     */
    public abstract UnitResults getResults();
}
