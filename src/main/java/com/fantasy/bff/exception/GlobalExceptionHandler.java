package com.fantasy.bff.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import com.fantasy.bff.config.RequestBodyByteCountFilter;
import com.fantasy.bff.dto.response.ErrorDto;
import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.EOFException;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorDto> handleSecurity(SecurityException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorDto.of("UNAUTHORIZED", ex.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorDto> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorDto.of("NOT_FOUND", ex.getMessage()));
    }

    /**
     * Signed in, and asking for something a premium subscription pays for. 403 rather than 402,
     * which is reserved and unimplemented: the request was understood and is refused for this
     * account. An expected outcome, so it is not logged, like the other 4xx above and below it.
     */
    @ExceptionHandler(PremiumRequiredException.class)
    public ResponseEntity<ErrorDto> handlePremiumRequired(PremiumRequiredException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorDto.of("PREMIUM_REQUIRED", ex.getMessage()));
    }

    /**
     * A checkout for an account that already has a live subscription. 409, since the request is
     * understood and conflicts with what the account already holds. Expected, so not logged.
     */
    @ExceptionHandler(SubscriptionAlreadyLiveException.class)
    public ResponseEntity<ErrorDto> handleSubscriptionAlreadyLive(SubscriptionAlreadyLiveException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorDto.of("SUBSCRIPTION_ALREADY_LIVE", ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorDto> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorDto.of("BAD_REQUEST", ex.getMessage()));
    }

    /**
     * A multipart upload past the container's size limit. The app scales a picture down before
     * uploading it, so this is a hand-built request rather than a fault on our side: a quiet 413,
     * not the 500 and the ERROR log the catch-all would give it.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorDto> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatusCode.valueOf(413))
                .body(ErrorDto.of("PAYLOAD_TOO_LARGE", "The upload is larger than allowed"));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorDto> handleDownstream(IllegalStateException ex) {
        // A 502/gateway fault on our side — log it so it surfaces (and reaches alerting).
        // Services that wrap a downstream failure as IllegalStateException land here, so
        // without this log the fault would be silently swallowed.
        log.error("Downstream unavailable", ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorDto.of("DOWNSTREAM_UNAVAILABLE", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorDto> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorDto.of("VALIDATION_ERROR", message));
    }

    /**
     * Bean Validation on a request parameter or path variable (a {@code @Validated} controller),
     * as opposed to {@code @Valid} on a body. Without this the violation reaches the catch-all
     * and a caller passing an out-of-range parameter gets a 500 for what is plainly their own
     * bad request.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorDto> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorDto.of("VALIDATION_ERROR", message));
    }

    /**
     * A downstream service returned a non-2xx response. Resource-level client errors
     * (404 not found, 409 conflict, 400 bad request) are genuine verdicts about the
     * request, so we relay them to the caller. Anything else (an auth/key mismatch,
     * a 5xx) is a failure on our side of the boundary and is surfaced as 502.
     */
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<ErrorDto> handleDownstreamResponse(RestClientResponseException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == HttpStatus.NOT_FOUND || status == HttpStatus.CONFLICT
                || status == HttpStatus.BAD_REQUEST) {
            log.warn("Relaying downstream {}: {}", status, ex.getResponseBodyAsString().replace("\r", "_").replace("\n", "_"));
            return ResponseEntity.status(status).body(ErrorDto.of(status.name(), ex.getMessage()));
        }
        log.error("Downstream service returned {}: {}", ex.getStatusCode(),
                ex.getResponseBodyAsString().replace("\r", "_").replace("\n", "_"), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorDto.of("DOWNSTREAM_UNAVAILABLE", "A downstream service is unavailable"));
    }

    /**
     * A downstream service was unreachable (connection refused, timeout) — no response
     * to inspect. Always a gateway failure on our side.
     */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ErrorDto> handleDownstreamCall(RestClientException ex) {
        log.error("Downstream service call failed", ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorDto.of("DOWNSTREAM_UNAVAILABLE", "A downstream service is unavailable"));
    }

    /**
     * Nothing is mapped to the requested path — a 404 about the request, not a fault on our
     * side. Without this the catch-all turns every probe of a non-existent path into a 500
     * with an ERROR log, and an internet-facing API is probed constantly (this fired on
     * {@code /v3/api-docs}, which is permitted but disabled outside dev/staging).
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorDto> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorDto.of("NOT_FOUND", "No resource found for the requested path"));
    }

    /**
     * The request body could not be read. Two very different causes land here and the response
     * is the same 400 for both — the caller sent something we could not use — but the log is
     * not: a client abort means the body stopped arriving mid-stream, which is a transport
     * problem worth measuring, not malformed JSON.
     *
     * <p>The abort branch logs how much of the body arrived against what was declared, because
     * that is what distinguishes a body cut at a fixed size (a proxy cap) from one that simply
     * stopped (a stalled or timed-out connection). Both are logged at WARN rather than ERROR:
     * they are not faults in this service, but silence would hide a user whose save is failing.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorDto> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                         HttpServletRequest request) {
        if (isClientAbort(ex)) {
            log.warn("Request body stopped arriving: {} {} — read {} of {} declared bytes",
                    request.getMethod(), request.getRequestURI(),
                    RequestBodyByteCountFilter.bytesRead(request), request.getContentLengthLong());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ErrorDto.of("REQUEST_INCOMPLETE", "The request body was not received in full"));
        }
        log.warn("Unreadable request body: {} {}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorDto.of("BAD_REQUEST", "The request body could not be read"));
    }

    /**
     * The response could not be written. The cause that matters here is the mirror image of an
     * aborted request body: the client went away mid-response — a browser navigating off the
     * player list, a phone losing signal — and the write failed with a broken pipe. Nothing is
     * wrong on our side, and nothing can be sent either, since the connection the error would
     * travel over is the one that just died. So: WARN, and no response at all.
     *
     * <p>A write failure that is <em>not</em> an abort is a genuine fault (a value we cannot
     * serialize) and keeps the ERROR log the catch-all would have given it.
     */
    @ExceptionHandler({HttpMessageNotWritableException.class, AsyncRequestNotUsableException.class,
            ClientAbortException.class})
    public ResponseEntity<ErrorDto> handleUnwritableResponse(Exception ex, HttpServletRequest request) {
        if (isClientAbort(ex)) {
            log.warn("Client went away before the response was written: {} {}",
                    request.getMethod(), request.getRequestURI());
            return null;
        }
        log.error("Response could not be written: {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorDto.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }

    /**
     * {@code AsyncRequestNotUsableException} counts as an abort in its own right: Spring raises it
     * once the response has already failed, so the connection is known to be unusable even when the
     * original {@code IOException} is no longer in the chain.
     */
    private static boolean isClientAbort(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof ClientAbortException || cause instanceof EOFException
                    || cause instanceof AsyncRequestNotUsableException) {
                return true;
            }
            if (cause.getCause() == cause) {
                return false;
            }
        }
        return false;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDto> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorDto.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
