package com.uber.jenkins.phabricator;

import jdk.nashorn.internal.parser.JSONParser;
import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by anup on 8/1/16.
 */
class InlineBuilder {
    private static String[] inlineJsonKeys = {
        "filePath",
        "isNewFile",
        "lineNumber",
        "lineLength",
        "content",
    };

    private final List<JSONObject> inline;

    public InlineBuilder() {
        this.inline = new ArrayList<JSONObject>();
    }
    /**
     *
     * @param inlineString
     */
    public void addInlineContext(String inlineString) throws JSONException {
        if (inlineString != null) {
            JSONArray jsonArray = JSONArray.fromObject(inlineString);
            for (int i = 0; i < jsonArray.size(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                inline.add(obj);
            }
        }
    }

    /**
     *
     * @return list of warnings to post
     */
    public List<JSONObject> getInlineJson() {
        return this.inline;
    }

    /**
     * Validate that inline JSON objects are in the correct format
     *
     *[
     *{
     *"filePath": "path/to_file.go",
     *"isNewFile": true,
     *"lineNumber": 10,
     *"lineLength": 1,
     *"content": "message content"
     *}
     *]
     * @return true - if json objects are in correct format with all the valid keys present
     * false - otherwise
     */
    public boolean validateInlineFormat() {
        for (JSONObject json : this.inline) {
            for (String key : inlineJsonKeys) {
                if (!json.containsKey(key)) {
                    return false;
                }
            }
        }
        return true;
    }
}
