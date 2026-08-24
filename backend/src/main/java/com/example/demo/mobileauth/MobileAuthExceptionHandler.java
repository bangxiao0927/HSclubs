package com.example.demo.mobileauth;

import com.example.demo.mobileauth.MobileAuthException.Error;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns a {@link MobileAuthException} into the contract's error body and the status the vectors
 * pin, so success and every failure path answer in one shape the app can branch on.
 *
 * <p>Scoped to the mobile-auth controllers, so it cannot change how the rest of the API reports
 * errors. See contracts/v1/schemas/mobile-auth-error.schema.json and
 * contracts/v1/vectors/mobile-auth.json.
 */
@RestControllerAdvice(assignableTypes = {MobileAuthStartController.class, MobileAuthCompleteController.class})
public class MobileAuthExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(MobileAuthExceptionHandler.class);

    @ExceptionHandler(MobileAuthException.class)
    public ResponseEntity<MobileAuthErrorResponse> handle(MobileAuthException exception) {
        return ResponseEntity.status(statusFor(exception.error()))
            .body(new MobileAuthErrorResponse(exception.error().code(), exception.getMessage()));
    }

    /**
     * Anything else these two controllers throw.
     *
     * <p>Without this, an unexpected failure left the mobile-auth endpoints answering with the
     * framework's own error body, which the app cannot branch on -- a shape it does not recognise
     * reads as "sign-in returned something the app did not understand" rather than as the
     * retryable fault it is. The cause is logged here and never sent: it would be the one place a
     * stack trace or an internal detail could reach a caller.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<MobileAuthErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unexpected mobile-auth failure", exception);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new MobileAuthErrorResponse(
                Error.TEMPORARILY_UNAVAILABLE.code(), "the sign-in could not be completed here"));
    }

    private static HttpStatus statusFor(Error error) {
        return switch (error) {
            case UNKNOWN_SCHOOL -> HttpStatus.NOT_FOUND;
            case TEMPORARILY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    @JsonPropertyOrder({"contract", "version", "error", "error_description"})
    public static class MobileAuthErrorResponse {
        private final String error;
        private final String errorDescription;

        public MobileAuthErrorResponse(String error, String errorDescription) {
            this.error = error;
            this.errorDescription = errorDescription;
        }

        public String getContract() {
            return "hsclubs.mobile-auth-error";
        }

        public int getVersion() {
            return 1;
        }

        public String getError() {
            return error;
        }

        @com.fasterxml.jackson.annotation.JsonProperty("error_description")
        public String getErrorDescription() {
            return errorDescription;
        }
    }
}
