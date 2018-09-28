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

import net.sf.json.JSONObject;

public class UnitResult {

    private static final String FAILURE = "fail";
    private static final String SKIP = "skip";
    private static final String PASS = "pass";
    private static final String UNSOUND = "unsound";
    private static final String ENGINE_NAME = "Jenkins";
    private final String className;
    private final String name;
    private final String stackTrace;
    private final float duration;
    private final int failCount;
    private final int skipCount;
    private final int passCount;

    public UnitResult(
            String className,
            String displayName,
            String stackTrace,
            float duration,
            int failCount,
            int skipCount,
            int passCount) {
        this.className = className;
        name = displayName;
        this.duration = duration;
        this.failCount = failCount;
        this.skipCount = skipCount;
        this.passCount = passCount;
        this.stackTrace = stackTrace;
    }

    public String getHarbormasterResult() {
        if (failCount > 0) {
            return FAILURE;
        } else if (skipCount > 0) {
            return SKIP;
        } else if (passCount > 0) {
            return PASS;
        }
        return UNSOUND;
    }

    public JSONObject toHarbormaster() {
        return new JSONObject()
                .element("name", name)
                .element("result", getHarbormasterResult())
                .element("namespace", className)
                .element("details", stackTrace)
                .element("engine", ENGINE_NAME)
                .element("duration", duration);
    }
}
