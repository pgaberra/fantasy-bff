package com.fantasy.bff.exception;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.security.JwtTokenValidator;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A request Spring MVC itself turns away (a method the path does not serve, a body type or an
 * {@code Accept} it cannot meet, a missing multipart part) is the caller's mistake and answers
 * with that 4xx. The catch-all used to take these for faults: 500, and an ERROR log that reaches
 * Sentry, for every scanner sending a GET to the sign-in endpoint.
 *
 * <p>A parameter left out, or one past its {@code @Size}, is the same kind of mistake. A
 * {@code @Validated} controller refuses an oversized one through its proxy (a
 * {@code ConstraintViolationException}); a controller without it through Spring MVC's own method
 * validation (a {@code HandlerMethodValidationException}, an {@code ErrorResponse}). Both are
 * pinned here, in the full context, since only a real proxy raises the first.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FrameworkClientErrorTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenValidator jwtTokenValidator;

    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    private final Logger handlerLog = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @BeforeEach
    void captureHandlerLog() {
        logged.start();
        handlerLog.addAppender(logged);
    }

    @AfterEach
    void releaseHandlerLog() {
        handlerLog.detachAppender(logged);
    }

    @Test
    void methodThePathDoesNotServe_isMethodNotAllowed() throws Exception {
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string(HttpHeaders.ALLOW, "POST"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));

        assertNothingLoggedAsAnError();
    }

    @Test
    void bodyTypeTheEndpointDoesNotRead_isUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("user@example.com"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));

        assertNothingLoggedAsAnError();
    }

    @Test
    void acceptTheEndpointCannotMeet_isNotAcceptable() throws Exception {
        mockMvc.perform(get("/api/v1/features").accept(MediaType.IMAGE_PNG))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("NOT_ACCEPTABLE"));

        assertNothingLoggedAsAnError();
    }

    @Test
    void uploadWithoutItsFilePart_isBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/v1/account/avatar")
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        assertNothingLoggedAsAnError();
    }

    @Test
    void requiredParameterLeftOut_isBadRequestNamingIt() throws Exception {
        mockMvc.perform(get("/api/v1/leagues/rostered-players")
                        .param("platform", "YAHOO")
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Missing or malformed parameter: leagueId"))
                .andExpect(resolvedBy(MissingServletRequestParameterException.class));

        assertNothingLoggedAsAnError();
    }

    @Test
    void parameterPastItsSizeOnAValidatedController_isBadRequestWithoutEchoingIt() throws Exception {
        String oversized = "x".repeat(65);

        mockMvc.perform(get("/api/v1/leagues/rostered-players")
                        .param("platform", "YAHOO")
                        .param("leagueId", oversized)
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(content().string(not(containsString(oversized))))
                .andExpect(resolvedBy(ConstraintViolationException.class));

        assertNothingLoggedAsAnError();
    }

    @Test
    void pathVariablePastItsSizeOnAControllerWithoutValidated_isBadRequest() throws Exception {
        String oversized = "x".repeat(65);

        mockMvc.perform(get("/api/v1/league-summaries/yahoo/" + oversized)
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(content().string(not(containsString(oversized))))
                .andExpect(resolvedBy(HandlerMethodValidationException.class));

        assertNothingLoggedAsAnError();
    }

    /**
     * Which refusal answered. Pinned so that a test meant for one path cannot quietly start
     * covering another, say if the controller without {@code @Validated} gains it.
     */
    private static ResultMatcher resolvedBy(Class<? extends Exception> refusal) {
        return result -> assertThat(result.getResolvedException()).isInstanceOf(refusal);
    }

    private String bearer() {
        return "Bearer " + jwtTokenValidator.generateToken(
                "00000000-0000-0000-0000-000000000001", "user@example.com", false);
    }

    private void assertNothingLoggedAsAnError() {
        assertThat(logged.list)
                .filteredOn(event -> event.getLevel().isGreaterOrEqual(Level.ERROR))
                .isEmpty();
    }
}
