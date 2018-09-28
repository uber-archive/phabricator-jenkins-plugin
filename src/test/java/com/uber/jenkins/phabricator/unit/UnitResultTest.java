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

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UnitResultTest {

    private UnitResult result;

    @Before
    public void setUp() {
        result = makeResult(0, 0, 1);
    }

    @Test
    public void testToHarbormaster() {
        JSONObject json = result.toHarbormaster();

        assertTrue(json.keySet().contains("engine"));
        assertEquals("display-name", json.getString("name"));
        assertEquals("class-name", json.getString("namespace"));
        assertEquals("pass", json.getString("result"));
        assertEquals(1.2, json.getDouble("duration"), 0.01);
        assertFalse(json.keySet().contains("details"));
    }

    @Test
    public void testToHarbormasterWithTrace() {
        JSONObject json = makeResult(1, 0, 0, "trace").toHarbormaster();

        assertTrue(json.keySet().contains("details"));
        assertEquals("trace", json.getString("details"));
    }

    @Test
    public void testGetHarbormasterResult() {
        assertEquals("fail", makeResult(1, 0, 0).getHarbormasterResult());
        assertEquals("skip", makeResult(0, 1, 0).getHarbormasterResult());
        assertEquals("pass", makeResult(0, 0, 1).getHarbormasterResult());
        assertEquals("unsound", makeResult(0, 0, 0).getHarbormasterResult());
    }

    private UnitResult makeResult(int fail, int skip, int pass) {
        return makeResult(fail, skip, pass, null);
    }

    private UnitResult makeResult(int fail, int skip, int pass, String trace) {
        return new UnitResult(
                "class-name",
                "display-name",
                trace,
                1.2f,
                fail,
                skip,
                pass
        );
    }
}
