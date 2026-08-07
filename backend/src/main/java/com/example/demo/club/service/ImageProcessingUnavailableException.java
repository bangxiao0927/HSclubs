package com.example.demo.club.service;

/**
 * Thrown by {@link ImageStorageService#store} when too many image decodes are already running
 * concurrently and a bounded wait for a free decode permit timed out. Callers should map this
 * to HTTP 503 Service Unavailable: this is a transient capacity limit on the server, not a
 * problem with the uploaded file itself (unlike {@link IllegalArgumentException}, which
 * {@code store()} also throws, for a 400).
 */
public class ImageProcessingUnavailableException extends RuntimeException {

    public ImageProcessingUnavailableException(String message) {
        super(message);
    }
}
