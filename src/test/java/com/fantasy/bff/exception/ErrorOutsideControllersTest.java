package com.fantasy.bff.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.security.JwtTokenValidator;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Whatever goes wrong outside a controller reaches the servlet container, and the container
 * answers it by dispatching to its error page. That dispatch carries no token, so the page has to
 * answer for the error it was sent, not re-judge the request: a fault in a filter reached the web
 * as a 401, which it reads as a lapsed session and answers with a token refresh, hiding the fault.
 * MockMvc performs no error dispatch, so these run against a real Tomcat.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorOutsideControllersTest extends BaseIntegrationTest {

    private static final String FAULT_HEADER = "X-Test-Filter-Fault";
    private static final String FAULT_MESSAGE = "fault in a servlet filter";

    @TestConfiguration
    static class FaultyFilterConfig {

        @Bean
        FilterRegistrationBean<Filter> faultyFilter() {
            Filter filter = (request, response, chain) -> {
                if (((HttpServletRequest) request).getHeader(FAULT_HEADER) != null) {
                    throw new IllegalStateException(FAULT_MESSAGE);
                }
                chain.doFilter(request, response);
            };
            return new FilterRegistrationBean<>(filter);
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JwtTokenValidator jwtTokenValidator;

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    private final Logger rootLog = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);

    @BeforeEach
    void captureLog() {
        logged.start();
        rootLog.addAppender(logged);
    }

    @AfterEach
    void releaseLog() {
        rootLog.detachAppender(logged);
    }

    @Test
    void faultInAFilter_answersAsTheFaultItIs() throws Exception {
        HttpResponse<String> response = send(request("/api/v1/features").header(FAULT_HEADER, "on"));

        assertThat(response.statusCode()).isEqualTo(500);
        assertJsonError(response, "INTERNAL_ERROR");
    }

    @Test
    void faultInAFilter_isLoggedAsAnErrorWithItsTraceExactlyOnce() throws Exception {
        send(request("/api/v1/features").header(FAULT_HEADER, "on"));

        assertThat(errors())
                .filteredOn(event -> carries(event.getThrowableProxy(), FAULT_MESSAGE))
                .hasSize(1);
    }

    @Test
    void signedInRequestSecurityRefuses_staysForbidden() throws Exception {
        String userToken = jwtTokenValidator.generateToken("user-1", "user@example.com", false);

        HttpResponse<String> response = send(request("/api/v1/admin/yahoo/connection")
                .header("Authorization", "Bearer " + userToken));

        assertThat(response.statusCode()).isEqualTo(403);
        assertJsonError(response, "FORBIDDEN");
        assertThat(errors()).isEmpty();
    }

    @Test
    void requestWithoutASession_isUnauthorizedWithTheSameErrorBody() throws Exception {
        HttpResponse<String> response = send(request("/api/v1/account"));

        assertThat(response.statusCode()).isEqualTo(401);
        assertJsonError(response, "UNAUTHORIZED");
        assertThat(errors()).isEmpty();
    }

    @Test
    void refusedWrite_isAnsweredLikeARefusedRead() throws Exception {
        HttpResponse<String> response = send(request("/api/v1/projections")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}")));

        assertThat(response.statusCode()).isEqualTo(401);
        assertJsonError(response, "UNAUTHORIZED");
    }

    @Test
    void errorAskedForAsAPicture_isStillAnsweredInJson() throws Exception {
        HttpResponse<String> response = send(request("/api/v1/account/avatar").header("Accept", "image/*"));

        assertThat(response.statusCode()).isEqualTo(401);
        assertJsonError(response, "UNAUTHORIZED");
    }

    @Test
    void errorPageAskedForDirectly_isStillRefused() throws Exception {
        HttpResponse<String> response = send(request("/error"));

        assertThat(response.statusCode()).isEqualTo(401);
        assertJsonError(response, "UNAUTHORIZED");
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
    }

    private static HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private static void assertJsonError(HttpResponse<String> response, String code) {
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/json"));
        assertThat(response.body()).contains("\"code\":\"" + code + "\"");
    }

    private List<ILoggingEvent> errors() {
        synchronized (logged) {
            return logged.list.stream()
                    .filter(event -> event.getLevel().isGreaterOrEqual(Level.ERROR))
                    .toList();
        }
    }

    private static boolean carries(IThrowableProxy thrown, String message) {
        for (IThrowableProxy cause = thrown; cause != null; cause = cause.getCause()) {
            if (message.equals(cause.getMessage())) {
                return true;
            }
        }
        return false;
    }
}
