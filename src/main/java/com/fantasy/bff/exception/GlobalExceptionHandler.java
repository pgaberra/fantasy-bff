package com.fantasy.bff.exception;

import com.fantasy.bff.dto.response.ErrorDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

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

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorDto> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorDto.of("BAD_REQUEST", ex.getMessage()));
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorDto> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorDto.of("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
