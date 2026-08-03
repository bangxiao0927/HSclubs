package com.example.demo.club.service;

/** Thrown when a club post id does not exist, or does not belong to the expected club. */
public class ClubPostNotFoundException extends RuntimeException {

    public ClubPostNotFoundException(Long postId) {
        super("Post " + postId + " was not found");
    }
}
