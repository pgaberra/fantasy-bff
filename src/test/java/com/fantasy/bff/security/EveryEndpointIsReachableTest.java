package com.fantasy.bff.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fantasy.bff.BaseIntegrationTest;
import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.client.EspnServiceClient;
import com.fantasy.bff.client.PlayerServiceClient;
import com.fantasy.bff.client.ProjectionServiceClient;
import com.fantasy.bff.client.YahooServiceClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Every endpoint this service maps has to be reachable by somebody.
 *
 * <p>The security config ends in {@code anyRequest().denyAll()}, which is the right default and
 * also a trap: a new controller is refused with 403 until its path is added there, and nothing
 * else in the build notices. It cost the league summary a round trip to staging — the endpoint
 * was written, specced, tested and deployed, and answered 403 to its own page.
 *
 * <p>So this walks the mappings rather than naming them: any GET a signed-in administrator is
 * refused outright fails here, whatever else the handler then does with the request. A handler
 * that 404s or 500s against stubbed downstreams is fine; being denied before it runs is not.
 */
// With the model's own kill switch on: an endpoint a switched-off feature denies is denied on
// purpose, and the question here is whether anything is denied by omission.
@SpringBootTest(properties = "security.projection-model-enabled=true")
@AutoConfigureMockMvc
class EveryEndpointIsReachableTest extends BaseIntegrationTest {

    /**
     * Paths that are meant to be refused, and are covered by tests of their own: the docs gate,
     * the container's own endpoints, and the error page the dispatcher owns.
     */
    private static final List<String> NOT_OURS = List.of(
            "/actuator", "/error", "/swagger-ui", "/v3/api-docs", "/api/v1/internal");

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenValidator jwtTokenValidator;

    // Nothing here is about what a handler answers, only about whether it is allowed to run, so
    // the downstreams are stubbed to keep the walk off the network.
    @MockitoBean private DatabaseServiceClient databaseServiceClient;
    @MockitoBean private YahooServiceClient yahooServiceClient;
    @MockitoBean private EspnServiceClient espnServiceClient;
    @MockitoBean private ProjectionServiceClient projectionServiceClient;
    @MockitoBean private PlayerServiceClient playerServiceClient;

    @Test
    @DisplayName("no mapped GET is refused before its handler runs")
    void noMappedGetIsDeniedOutright() throws Exception {
        String token = jwtTokenValidator.generateToken("user-1", "admin@example.com", true);
        Set<String> denied = new TreeSet<>();

        for (String path : mappedGetPaths()) {
            MvcResult result = mockMvc.perform(get(path)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andReturn();
            if (result.getResponse().getStatus() == 403) {
                denied.add(path);
            }
        }

        assertThat(denied)
                .as("GETs the security config denies outright — add them to SecurityConfig")
                .isEmpty();
    }

    /** Every mapped GET, with a stand-in for each path variable. */
    private List<String> mappedGetPaths() {
        // By name: the actuator contributes a mapping of the same type, and its endpoints are
        // not this service's to reach.
        RequestMappingHandlerMapping mapping = mockMvc.getDispatcherServlet()
                .getWebApplicationContext()
                .getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        List<String> paths = new ArrayList<>();
        for (RequestMappingInfo info : mapping.getHandlerMethods().keySet()) {
            boolean answersGet = info.getMethodsCondition().getMethods().isEmpty()
                    || info.getMethodsCondition().getMethods().stream()
                            .anyMatch(method -> method.asHttpMethod() == HttpMethod.GET);
            if (!answersGet) {
                continue;
            }
            for (String pattern : info.getPatternValues()) {
                if (NOT_OURS.stream().anyMatch(pattern::startsWith)) {
                    continue;
                }
                paths.add(fill(pattern));
            }
        }
        return paths;
    }

    /** A path variable stands in as 1, which is a plausible id and a harmless string. */
    private String fill(String pattern) {
        return pattern.replaceAll("\\{[^/}]+}", "1");
    }
}
