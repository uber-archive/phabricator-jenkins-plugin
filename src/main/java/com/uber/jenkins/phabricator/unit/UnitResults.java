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

import java.util.ArrayList;
import java.util.List;

/**
 * Represent a list of unit results
 */
public class UnitResults {
    private final List<UnitResult> results;

    public UnitResults() {
        this.results = new ArrayList<UnitResult>();
    }

    public void add(UnitResult result) {
        results.add(result);
    }

    public List<UnitResult> getResults() {
        return results;
    }

    /**
     * Convert a suite of unit results to Harbormaster JSON format
     * @return Harbormaster-formatted unit results
     */
    public List<JSONObject> toHarbormaster() {
        List<JSONObject> harbormasterData = new ArrayList<JSONObject>();

        for (UnitResult result : results) {
            harbormasterData.add(result.toHarbormaster());
        }

        return harbormasterData;
    }
}
