package com.fantasy.bff.exception;

import com.fantasy.bff.dto.response.ErrorDto;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.io.EOFException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A body that stops arriving mid-stream must not be reported as an unexpected server fault.
 * Saved projections were failing in production this way: the upload died part-way and the
 * catch-all turned it into a 500 with an ERROR log, which said nothing about how far the body
 * got — the one measurement that separates a proxy cap from a stalled connection.
 */
class GlobalExceptionHandlerUnreadableBodyTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void clientAbortMidBody_isReportedAsAnIncompleteRequest() {
        ResponseEntity<ErrorDto> response = handler.handleUnreadableBody(
                unreadableBody(new ClientAbortException(new EOFException())),
                request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("REQUEST_INCOMPLETE");
    }

    @Test
    void abortNestedDeeperInTheCauseChain_isStillRecognised() {
        Throwable nested = new IllegalStateException("wrapped", new ClientAbortException(new EOFException()));

        ResponseEntity<ErrorDto> response = handler.handleUnreadableBody(unreadableBody(nested), request());

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("REQUEST_INCOMPLETE");
    }

    @Test
    void malformedJson_isABadRequestRatherThanAnIncompleteOne() {
        ResponseEntity<ErrorDto> response = handler.handleUnreadableBody(
                unreadableBody(new IllegalArgumentException("Unexpected character")),
                request());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("BAD_REQUEST");
    }

    private static HttpMessageNotReadableException unreadableBody(Throwable cause) {
        return new HttpMessageNotReadableException("JSON parse error", cause, emptyInputMessage());
    }

    private static HttpServletRequest request() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/v1/projections");
        when(request.getContentLengthLong()).thenReturn(520_000L);
        return request;
    }

    private static HttpInputMessage emptyInputMessage() {
        return new HttpInputMessage() {
            @Override
            public InputStream getBody() {
                return InputStream.nullInputStream();
            }

            @Override
            public HttpHeaders getHeaders() {
                return HttpHeaders.EMPTY;
            }
        };
    }
}
