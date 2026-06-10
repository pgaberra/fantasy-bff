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
     * A downstream service (db-service, nhl-service) returned a non-2xx status or was
     * unreachable. Surfaced as 502 with the upstream status logged, so an auth/key
     * mismatch (401) or outage doesn't masquerade as an opaque 500.
     */
    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ErrorDto> handleDownstreamCall(RestClientException ex) {
        if (ex instanceof RestClientResponseException response) {
            log.error("Downstream service returned {}: {}", response.getStatusCode(),
                    response.getResponseBodyAsString(), ex);
        } else {
            log.error("Downstream service call failed", ex);
        }
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
