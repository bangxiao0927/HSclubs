package com.example.demo.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Narrowly scoped to {@link MaxUploadSizeExceededException}: Spring's own rejection of a
 * multipart upload whose file or overall request size exceeds
 * {@code spring.servlet.multipart.max-file-size}/{@code max-request-size} (see
 * {@code application.yaml} and docs/API.md's 413 row). This is thrown by
 * {@code StandardServletMultipartResolver} while {@code DispatcherServlet} is still resolving
 * the request -- strictly before handler mapping, so before any controller method (and its own
 * application-level 400s, e.g. {@code ImageStorageService}'s 5MB/2MB checks) ever runs.
 * <p>
 * The exception already implements {@link org.springframework.web.ErrorResponse}, so it
 * resolves to 413 even without this class (verified directly: temporarily removing
 * {@code @RestControllerAdvice} still yields 413, but with an empty response body --
 * {@code DefaultHandlerExceptionResolver#handleErrorResponse} calls
 * {@code HttpServletResponse#sendError}, which does not itself write a body). This handler
 * makes that body an explicit, readable, English detail message (and regression-testable)
 * without touching any other exception type or any controller's own error handling -- see
 * MultipartUploadExceptionHandlerIntegrationTest for a full-stack regression test that actually
 * crosses the configured ceiling.
 */
@RestControllerAdvice
public class MultipartUploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE,
            "The uploaded file is too large. Please choose a smaller file and try again.");
    }
}
