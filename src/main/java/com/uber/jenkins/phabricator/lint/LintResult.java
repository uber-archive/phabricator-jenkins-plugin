/*
 * Copyright (c) 2016 Uber
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.uber.jenkins.phabricator.lint;

import net.sf.json.JSONObject;

/**
 * This currently mirrors the API format of Harbormaster lint messages
 *
 * Reference: https://secure.phabricator.com/conduit/method/harbormaster.sendmessage/
 *
 * name         string          Short message name, like "Syntax Error".
 * code         string          Lint message code identifying the type of message, like "ERR123".
 * severity     string          Severity of the message.
 * path         string          Path to the file containing the lint message, from the project root.
 * line         optional int    Line number in the file where the text which triggered the message first appears. The first line of the file is line 1, not line 0.
 * char         optional int    Byte position on the line where the text which triggered the message starts. The first byte on the line is byte 1, not byte 0. This position is byte-based (not character-based) because not all lintable files have a valid character encoding.
 * description  optional string Long explanation of the lint message.
 */
public class LintResult {

    final String name;
    final String code;
    final String severity;
    final String path;
    final Integer line;
    final Integer charPosition; // NOTE "char" parameter in JSON
    final String description;

    public LintResult(
            String name, String code, String severity, String path, Integer line, Integer charPosition,
            String description) {
        this.name = name;
        this.code = code;
        this.severity = severity;
        this.path = path;
        this.line = line;
        this.charPosition = charPosition;
        this.description = description;
    }

    public static LintResult fromJsonObject(JSONObject json) {
        String name = (String) json.get("name");
        String code = (String) json.get("code");
        String severity = (String) json.get("severity");
        String path = (String) json.get("path");
        Integer line = (Integer) json.opt("line");
        Integer charPosition = (Integer) json.opt("char");
        String description = (String) json.opt("description");

        return new LintResult(name, code, severity, path, line, charPosition, description);
    }

    /**
     * Create a Harbormaster-API-compatible representation of the lint result
     *
     * @return A JSON representation of the lint result
     */
    public JSONObject toHarbormaster() {
        return new JSONObject()
                .element("name", name)
                .element("code", code)
                .element("severity", severity)
                .element("path", path)
                .element("line", line)
                .element("char", charPosition)
                .element("description", description);
    }
}
