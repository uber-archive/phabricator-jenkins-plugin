// Copyright (c) 2016 Uber
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

package com.uber.jenkins.phabricator.lint;

import net.sf.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LintResultTest {
    private LintResult results;

    @Before
    public void setUp() {
        results = new LintResult("name", "code", "severity", "path", null, null, "description");
    }

    @Test
    public void testToHarbormaster() {
        assertEquals(7, results.toHarbormaster().size());
    }

    @Test
    public void testFromJsonObject() {
        results = LintResult.fromJsonObject(
                JSONObject.fromObject(
                        "{ \"name\": \"NewApi\"," +
                                "\"code\": \"_code\"," +
                                "\"severity\": \"error\", " +
                                "\"path\": \"foobar.java\","));
        assertTrue(results.name == "NewApi");
        assertTrue(results.code == "_code");
        assertTrue(results.severity == "error");
        assertTrue(results.path == "foobar.java");
        assertTrue(results.line == null);
        assertTrue(results.charPosition == null);
        assertTrue(results.description == null);
    }
}
