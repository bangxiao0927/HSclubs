package com.example.demo.mobileauth;

/**
 * A mobile-auth failure, carrying the closed error code the contract defines.
 *
 * <p>The code is what the app branches on -- retry, restart, or give up -- so it comes from the
 * fixed set in contracts/v1/schemas/mobile-auth-error.schema.json. The message is for a log line a
 * human reads and never carries a code, verifier, token or anything else the flow keeps private.
 */
public class MobileAuthException extends RuntimeException {

    public enum Error {
        INVALID_REQUEST("invalid_request"),
        UNKNOWN_SCHOOL("unknown_school"),
        INVALID_STATE("invalid_state"),
        INVALID_GRANT("invalid_grant"),
        EXPIRED_CODE("expired_code"),
        ACCESS_DENIED("access_denied"),
        TEMPORARILY_UNAVAILABLE("temporarily_unavailable");

        private final String code;

        Error(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    private final Error error;

    public MobileAuthException(Error error, String message) {
        super(message);
        this.error = error;
    }

    public Error error() {
        return error;
    }
}
