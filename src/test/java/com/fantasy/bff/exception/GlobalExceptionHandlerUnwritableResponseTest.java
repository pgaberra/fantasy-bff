package com.fantasy.bff.exception;

import com.fantasy.bff.dto.response.ErrorDto;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.catalina.connector.ClientAbortException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A client that goes away mid-response must not be reported as an unexpected server fault. The
 * player list was failing this way: the browser dropped the connection part-way through the JSON,
 * the broken pipe reached the catch-all, and every disconnect became an ERROR — an alert about a
 * service that was working exactly as intended.
 */
class GlobalExceptionHandlerUnwritableResponseTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void brokenPipeMidResponse_answersWithNothingAtAll() {
        ResponseEntity<ErrorDto> response = handler.handleUnwritableResponse(
                unwritableResponse(new ClientAbortException(new IOException("Broken pipe"))),
                request());

        assertThat(response).isNull();
    }

    @Test
    void abortNestedDeeperInTheCauseChain_isStillRecognised() {
        Throwable nested = new IllegalStateException("wrapped",
                new ClientAbortException(new IOException("Broken pipe")));

        assertThat(handler.handleUnwritableResponse(unwritableResponse(nested), request())).isNull();
    }

    @Test
    void responseAlreadyUnusable_countsAsAnAbortWithoutTheOriginalIoFailure() {
        AsyncRequestNotUsableException unusable =
                new AsyncRequestNotUsableException("Response not usable after response errors.");

        assertThat(handler.handleUnwritableResponse(unusable, request())).isNull();
    }

    @Test
    void serialisationFailure_isStillAnInternalError() {
        ResponseEntity<ErrorDto> response = handler.handleUnwritableResponse(
                unwritableResponse(new IllegalArgumentException("No serializer found")),
                request());

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
    }

    private static HttpMessageNotWritableException unwritableResponse(Throwable cause) {
        return new HttpMessageNotWritableException("Could not write JSON", cause);
    }

    private static HttpServletRequest request() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/v1/players/skaters");
        return request;
    }
}
