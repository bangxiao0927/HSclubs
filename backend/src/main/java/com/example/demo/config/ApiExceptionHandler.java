package com.example.demo.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ensures every {@code /api/**} error response this codebase's own controllers/services choose
 * to produce is a real, English {@code application/problem+json} body over a real embedded
 * server -- not merely the right HTTP status with an empty or generic body. Deliberately narrow:
 * only the two exception types below are handled, so this can never intercept anything else,
 * least of all Spring Security's own authentication/authorization rejections (see below).
 * <p>
 * Both {@link MaxUploadSizeExceededException} and {@link ResponseStatusException} already
 * implement {@link org.springframework.web.ErrorResponse} and so already resolve to the right
 * HTTP status even without this class -- verified directly, for both, by temporarily removing
 * the relevant {@code @ExceptionHandler} method and observing the real response: status is
 * unchanged, but the body is either empty ({@code MaxUploadSizeExceededException}, whose
 * {@code DefaultHandlerExceptionResolver#handleErrorResponse} path calls
 * {@code HttpServletResponse#sendError} without writing a body) or Spring Boot's own generic
 * error JSON with no message at all ({@code ResponseStatusException}, whose dedicated
 * {@code ResponseStatusExceptionResolver} calls {@code sendError} too, which -- for an
 * unhandled {@code sendError} -- triggers {@code BasicErrorController}'s
 * {@code {"timestamp","status","error","path"}} body; {@code server.error.include-message}
 * defaults to {@code never}, so the original reason is silently dropped). Both handler methods
 * below exist only to make that already-correct status carry its own message explicitly, as a
 * real, testable JSON body, not to change what status is returned.
 * <p>
 * This class must never gain a handler for {@code org.springframework.security.access.
 * AccessDeniedException} or any {@code AuthenticationException}: those are Spring Security's own
 * concern, resolved by {@code ExceptionTranslationFilter} via {@code SecurityConfig}'s configured
 * {@code AccessDeniedHandler}/{@code AuthenticationEntryPoint} (a bare
 * {@code HttpStatusEntryPoint(UNAUTHORIZED)} for this app), entirely outside
 * {@code DispatcherServlet}'s own exception resolution -- adding one here would change that
 * entrypoint's behavior for every authenticated route, not just this app's own controllers. This
 * app's own 403s are already {@link ResponseStatusException}s raised from inside a controller
 * method after authentication has already succeeded (see e.g.
 * {@code ClubPostController#deletePost}), a distinct, later decision from "is this caller
 * authenticated at all" -- which is exactly why extending the {@code ResponseStatusException}
 * handler below to also cover 403 is safe.
 * <p>
 * See ApiExceptionHandlerIntegrationTest for full-stack regression coverage (a real embedded
 * Tomcat connector, not MockMvc) of both handler methods.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE,
            "The uploaded file is too large. Please choose a smaller file and try again.");
    }

    // ResponseStatusException already carries its own correctly-shaped ProblemDetail: its
    // constructor (see ResponseStatusException#<init>) calls setDetail(reason) itself, so
    // ex.getBody() already has the right status and the exact English reason every call site in
    // this codebase already passes (e.g. `new ResponseStatusException(HttpStatus.BAD_REQUEST,
    // e.getMessage())`) -- nothing here re-derives or rewords it.
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatusException(ResponseStatusException ex) {
        return ex.getBody();
    }
}
