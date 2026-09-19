package com.fantasy.bff.exception;

import com.fantasy.bff.dto.response.ErrorDto;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The servlet container's error page, for whatever ended outside a controller: an exception
 * thrown in a filter, or a {@code sendError} from Spring Security's refusals. It answers with the
 * status the error was given and the same {@link ErrorDto} as {@link GlobalExceptionHandler},
 * as JSON whatever the request's {@code Accept}.
 *
 * <p>It never re-judges the request. {@code SecurityConfig} lets the error dispatch through
 * unauthenticated, since that dispatch carries no token: judged again, every such error became a
 * 401, a fault in a filter and a signed-in 403 alike, and the web read each as a lapsed session.
 *
 * <p>A dispatch that carries an exception is not logged here: Tomcat has already logged it at
 * ERROR with its trace. A 5xx without one has had nothing log it, so this does.
 */
@Hidden
@RestController
public class ErrorPageController implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(ErrorPageController.class);

    @RequestMapping("${spring.web.error.path:${error.path:/error}}")
    public ResponseEntity<ErrorDto> error(HttpServletRequest request) {
        HttpStatusCode status = statusOf(request);
        if (status.is5xxServerError() && request.getAttribute(RequestDispatcher.ERROR_EXCEPTION) == null) {
            log.error("{} {} ended in {} outside the controllers", request.getMethod(),
                    String.valueOf(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI))
                            .replace("\r", "_").replace("\n", "_"),
                    status.value());
        }
        return GlobalExceptionHandler.forStatus(status, HttpHeaders.EMPTY);
    }

    private static HttpStatusCode statusOf(HttpServletRequest request) {
        return request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code
                ? HttpStatusCode.valueOf(code)
                : HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
