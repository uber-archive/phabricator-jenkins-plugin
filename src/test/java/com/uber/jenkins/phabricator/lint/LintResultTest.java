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
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class LintResultTest {
    @Test
    public void testToHarbormaster() {
        LintResult results = new LintResult("name", "code", "severity", "path", 1, 2, "description");
        assertEquals(7, results.toHarbormaster().size());
    }

    @Test
    public void testFromJsonObject() {
        LintResult results = LintResult.fromJsonObject(
                JSONObject.fromObject(
                        "{ \"name\": \"NewApi\"," +
                                "\"code\": \"_code\"," +
                                "\"severity\": \"error\", " +
                                "\"path\": \"foobar.java\"}"));
        assertEquals(results.name, "NewApi");
        assertEquals(results.code, "_code");
        assertEquals(results.severity, "error");
        assertEquals(results.path, "foobar.java");
        assertTrue(results.line == null);
        assertTrue(results.charPosition == null);
        assertTrue(results.description == null);
    }
}
