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

package com.uber.jenkins.phabricator.tasks;

import com.uber.jenkins.phabricator.conduit.ConduitAPIException;
import com.uber.jenkins.phabricator.conduit.DifferentialClient;
import com.uber.jenkins.phabricator.utils.Logger;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;

import java.io.IOException;

/**
 * Post comment task.
 */
public class PostCommentTask extends Task {

    private static final boolean SILENT = false;
    private static final String DEFAULT_COMMENT_ACTION = "none";

    private final DifferentialClient differentialClient;
    private final String revisionID;
    private final String comment;
    private final String commentAction;

    /**
     * PostCommentTask constructor.
     * @param logger the logger
     * @param differentialClient the client for the differential
     * @param revisionID the revision identifier from harbormaster
     * @param comment the body of the comment
     * @param commentAction the name of the comment action
     */
    public PostCommentTask(Logger logger, DifferentialClient differentialClient,
                           String revisionID, String comment, String commentAction) {
        super(logger);

        this.differentialClient = differentialClient;
        this.revisionID = revisionID;
        this.comment = comment;
        this.commentAction = commentAction;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getTag() {
        return "post-comment";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void setup() {
        // Do nothing.
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void execute() {
        JSONObject postDifferentialCommentResult = postDifferentialComment(comment, SILENT,
                commentAction);
        if (postDifferentialCommentResult == null ||
                !(postDifferentialCommentResult.get("error_info") instanceof JSONNull)) {
            if (postDifferentialCommentResult != null) {
                info(String.format("Got error %s with action %s",
                        postDifferentialCommentResult.get("error_info"), commentAction));
            }

            info("Re-trying with action 'none'");
            postDifferentialComment(comment, SILENT, DEFAULT_COMMENT_ACTION);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void tearDown() {
        // Do nothing.
    }

    private JSONObject postDifferentialComment(String message, boolean silent, String action) {
        try {
            JSONObject postDifferentialCommentResult = differentialClient.postComment(revisionID,
                    message, silent, action);
            result = Result.SUCCESS;
            return postDifferentialCommentResult;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (ConduitAPIException e) {
            info("unable to post comment");
        }

        result = Result.FAILURE;
        return null;
    }
}
