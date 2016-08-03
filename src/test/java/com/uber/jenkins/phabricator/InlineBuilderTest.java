// Copyright (c) 2015 Uber Technologies, Inc.
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

package com.uber.jenkins.phabricator;

import com.uber.jenkins.phabricator.utils.Logger;
import com.uber.jenkins.phabricator.utils.TestUtils;
import net.sf.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.*;

public class InlineBuilderTest {
    private static final Logger logger = TestUtils.getDefaultLogger();
    private static final String FAKE_BUILD_URL = "http://example.com/job/123";
    private static final String FAKE_BRANCH_NAME = "oober-is-great";

    private InlineBuilder inlineBuilder;

    @Before
    public void setUp() {
        inlineBuilder = new InlineBuilder();
    }

    @Test
    public void testSingleInlineJson() {
        inlineBuilder.addInlineContext(singleJson());

        List<JSONObject> inlineContext = inlineBuilder.getInlineJson();
        assertEquals("can't fetch single warning", 1, inlineContext.size());
        for (JSONObject inline : inlineContext) {
            assertEquals(inline.get("lineNumber"), 10);
        }
    }

    @Test
    public void testMultipleInlineJson() {
        inlineBuilder.addInlineContext(multipleInlineJson());

        List<JSONObject> warnings = inlineBuilder.getInlineJson();
        assertEquals("can't fetch single warning", 2, warnings.size());
    }

    @Test
    public void testValidateInlineFormat() {
        inlineBuilder.addInlineContext(singleJson());
        assertTrue(inlineBuilder.validateInlineFormat());
    }

    @Test
    public void testInvalidateInlineFormat() {
        inlineBuilder.addInlineContext(invalidInlineJson());
        assertFalse(inlineBuilder.validateInlineFormat());
    }

    private String invalidInlineJson() {
        return "[\n" +
                "{\n" +
                "  \"isNewFile\": true,\n" +
                "  \"lineNumber\": 10,\n" +
                "  \"lineLength\": 1,\n" +
                "  \"content\": \"message content\"\n" +
                "}\n" +
                "]";
    }

    private String singleJson() {
        return "[\n" +
                "{\n" +
                "  \"filePath\": \"path/to_file.go\",\n" +
                "  \"isNewFile\": true,\n" +
                "  \"lineNumber\": 10,\n" +
                "  \"lineLength\": 1,\n" +
                "  \"content\": \"message content\"\n" +
                "}\n" +
                "]";
    }

    private String multipleInlineJson() {
        return "[\n" +
                "{\n" +
                "  \"filePath\": \"path/to_file.go\",\n" +
                "  \"isNewFile\": true,\n" +
                "  \"lineNumber\": 10,\n" +
                "  \"lineLength\": 1,\n" +
                "  \"content\": \"message content\"\n" +
                "},\n" +
                "{\n" +
                "  \"filePath\": \"path/to_file.go\",\n" +
                "  \"isNewFile\": false,\n" +
                "  \"lineNumber\": 100,\n" +
                "  \"lineLength\": 1,\n" +
                "  \"content\": \"message content\"\n" +
                "}\n" +
                "]";
    }
}

