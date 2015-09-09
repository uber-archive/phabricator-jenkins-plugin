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
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.SuiteResult;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction;

/**
 * Provides jUnit test reports to report results on builds
 */
@SuppressWarnings("unused")
public class JUnitTestProvider extends UnitTestProvider {
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean resultsAvailable() {
        return getJUnitResults() != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UnitResults getResults() {
        return convertJUnit(getJUnitResults());
    }

    /**
     * Convert JUnit's TestResult representation into a generic UnitResults
     * @param jUnitResults The result of the JUnit run
     * @return The converted results
     */
    public UnitResults convertJUnit(TestResult jUnitResults) {
        UnitResults results = new UnitResults();
        if (jUnitResults == null) {
            return results;
        }
        for (SuiteResult sr : jUnitResults.getSuites()) {
            for (CaseResult cr : sr.getCases()) {
                UnitResult result = new UnitResult(
                        cr.getClassName(),
                        cr.getDisplayName(),
                        cr.getDuration(),
                        cr.getFailCount(),
                        cr.getSkipCount(),
                        cr.getPassCount()
                );
                results.add(result);
            }
        }
        return results;
    }

    private TestResult getJUnitResults() {
        AbstractBuild build = getBuild();

        TestResultAction jUnitAction = build.getAction(TestResultAction.class);
        if (jUnitAction == null) {
            return null;
        }
        return jUnitAction.getResult();
    }
}
