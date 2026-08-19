package com.example.demo.mobileauth;

import com.example.demo.mobileauth.MobileAuthException.Error;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(MobileAuthException.class)
    public ResponseEntity<MobileAuthErrorResponse> handle(MobileAuthException exception) {
        return ResponseEntity.status(statusFor(exception.error()))
            .body(new MobileAuthErrorResponse(exception.error().code(), exception.getMessage()));
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
