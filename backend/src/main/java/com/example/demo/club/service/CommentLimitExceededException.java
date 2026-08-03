package com.example.demo.club.service;

/** Thrown when a post already carries the maximum of 50 comments (see #79). */
public class CommentLimitExceededException extends RuntimeException {

    public CommentLimitExceededException(Long postId) {
        super("Post " + postId + " already has the maximum number of comments");
    }
}
