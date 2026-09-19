package com.fantasy.bff.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fantasy.bff.dto.response.ErrorDto;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The error page answers with the status the error already has, in the advice's ErrorDto, and
 * logs only the 5xx nothing else has logged: an exception on the dispatch was logged by Tomcat.
 */
class ErrorPageControllerTest {

    private final ErrorPageController controller = new ErrorPageController();

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    private final Logger pageLog = (Logger) LoggerFactory.getLogger(ErrorPageController.class);

    @BeforeEach
    void captureLog() {
        logged.start();
        pageLog.addAppender(logged);
    }

    @AfterEach
    void releaseLog() {
        pageLog.detachAppender(logged);
    }

    @Test
    void refusalKeepsItsStatus_andIsNotLogged() {
        ResponseEntity<ErrorDto> response = controller.error(errorDispatch(403, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isEqualTo(ErrorDto.of("FORBIDDEN", "Forbidden"));
        assertThat(logged.list).isEmpty();
    }

    @Test
    void faultCarryingAnException_isAnInternalErrorLeftToTomcatsLog() {
        ResponseEntity<ErrorDto> response =
                controller.error(errorDispatch(500, new IllegalStateException("fault in a filter")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(logged.list).isEmpty();
    }

    @Test
    void faultWithNothingToShowForIt_isLoggedHere() {
        ResponseEntity<ErrorDto> response = controller.error(errorDispatch(503, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(logged.list).singleElement()
                .satisfies(event -> assertThat(event.getLevel()).isEqualTo(Level.ERROR));
    }

    @Test
    void requestWithNoErrorOnIt_isAnInternalError() {
        ResponseEntity<ErrorDto> response = controller.error(new MockHttpServletRequest("GET", "/error"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static MockHttpServletRequest errorDispatch(int status, Throwable exception) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status);
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/api/v1/features");
        if (exception != null) {
            request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, exception);
        }
        return request;
    }
}
