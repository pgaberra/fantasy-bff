package com.fantasy.bff.client;

import com.fantasy.bff.support.WireMockConfigs;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A read that db-service did not answer in time is asked once more rather than becoming a 502.
 *
 * <p>The three-second ceiling on the ordinary client is a deadline on the whole exchange, so a slow
 * moment in db-service cancelled a call the caller was owed an answer to: JAVA-SPRING-BOOT-1C and
 * 2Q on the summary list, 2J on the login lookup. The two shapes are the same fault seen at
 * different moments — cancelled before the read, and cancelled while Jackson was still on the
 * stream — so both are covered here, the second one because it is the one a request interceptor
 * would not have caught: by the time the body fails, the interceptor has long returned its response.
 *
 * <p>The negative cases carry as much weight as the positive ones. A status db-service chose to
 * send is an answer and must not be asked for twice — retrying a 500 would double the load on a
 * service already in trouble — and neither must a body that will not parse for its own reasons.
 */
class HttpDatabaseServiceClientReadRetryTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PROJECTIONS_PATH = "/api/v1/users/[^/]+/projections";
    private static final String USERS_PATH = "/api/v1/users";

    /**
     * A real cancellation is provoked rather than simulated, so the suite does have to wait — but
     * only for these few tests, and the second of the two numbers is what it waits for.
     *
     * <p>The margin between them is deliberately wide in both directions. Too tight a timeout and
     * the first call in a cold JVM misses it while WireMock answered at once, which makes a
     * *successful* read look cancelled and every count here wrong by one; too narrow a delay and the
     * stub answers before the deadline. A second is far more than a loopback call to a stub needs,
     * and two is unambiguously past it.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(1);
    private static final int LONGER_THAN_THE_TIMEOUT_MS = 2000;

    private WireMockServer db;
    private HttpDatabaseServiceClient client;

    @BeforeEach
    void setUp() {
        db = new WireMockServer(WireMockConfigs.http11());
        db.start();
        RestClient restClient = restClient(db);
        client = new HttpDatabaseServiceClient(restClient, restClient, restClient);
    }

    @AfterEach
    void tearDown() {
        db.stop();
    }

    @Test
    void listProjections_asksAgainWhenTheFirstAttemptIsCancelled() {
        db.stubFor(get(urlPathMatching(PROJECTIONS_PATH)).inScenario("slow once")
                .whenScenarioStateIs(STARTED)
                .willReturn(okJson("[]").withFixedDelay(LONGER_THAN_THE_TIMEOUT_MS))
                .willSetStateTo("warm"));
        db.stubFor(get(urlPathMatching(PROJECTIONS_PATH)).inScenario("slow once")
                .whenScenarioStateIs("warm")
                .willReturn(okJson("[{\"name\":\"My board\"}]")));

        assertThat(client.listProjections(USER_ID)).hasSize(1);
        assertThat(requests()).isEqualTo(2);
    }

    @Test
    void findUserByEmail_asksAgainWhenTheFirstAttemptIsCancelled() {
        db.stubFor(get(urlPathMatching(USERS_PATH)).inScenario("slow once")
                .whenScenarioStateIs(STARTED)
                .willReturn(okJson(user()).withFixedDelay(LONGER_THAN_THE_TIMEOUT_MS))
                .willSetStateTo("warm"));
        db.stubFor(get(urlPathMatching(USERS_PATH)).inScenario("slow once")
                .whenScenarioStateIs("warm")
                .willReturn(okJson(user())));

        assertThat(client.findUserByEmail("someone@example.test")).isPresent();
        assertThat(requests()).isEqualTo(2);
    }

    /**
     * The JAVA-SPRING-BOOT-2Q shape: the response head arrives and the body dies under the reader,
     * so the failure surfaces from deserialisation rather than from sending. A malformed chunk
     * stands in for the cancellation, which cannot be timed to land mid-parse on purpose.
     */
    @Test
    void listProjections_asksAgainWhenTheBodyDiesWhileItIsBeingRead() {
        db.stubFor(get(urlPathMatching(PROJECTIONS_PATH)).inScenario("broken once")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withFault(Fault.MALFORMED_RESPONSE_CHUNK))
                .willSetStateTo("warm"));
        db.stubFor(get(urlPathMatching(PROJECTIONS_PATH)).inScenario("broken once")
                .whenScenarioStateIs("warm")
                .willReturn(okJson("[{\"name\":\"My board\"}]")));

        assertThat(client.listProjections(USER_ID)).hasSize(1);
        assertThat(requests()).isEqualTo(2);
    }

    /** One more attempt, not attempts until it works: a db-service that is down stays down. */
    @Test
    void listProjections_givesUpAfterOneMoreAttempt() {
        db.stubFor(get(urlPathMatching(PROJECTIONS_PATH))
                .willReturn(okJson("[]").withFixedDelay(LONGER_THAN_THE_TIMEOUT_MS)));

        assertThatThrownBy(() -> client.listProjections(USER_ID))
                .isInstanceOf(RestClientException.class);
        assertThat(requests()).isEqualTo(2);
    }

    @Test
    void listProjections_doesNotAskAgainWhenDbServiceAnswersWithAStatus() {
        db.stubFor(get(urlPathMatching(PROJECTIONS_PATH)).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.listProjections(USER_ID))
                .isInstanceOf(RestClientException.class);
        assertThat(requests()).isEqualTo(1);
    }

    /** A 404 is the answer "no such user", which findUserByEmail reads. Asking twice cannot help. */
    @Test
    void findUserByEmail_doesNotAskAgainForA404() {
        db.stubFor(get(urlPathMatching(USERS_PATH)).willReturn(aResponse().withStatus(404)));

        assertThat(client.findUserByEmail("nobody@example.test")).isEmpty();
        assertThat(requests()).isEqualTo(1);
    }

    /** A body that is simply not what we expect is a defect to see, not a call to repeat. */
    @Test
    void listProjections_doesNotAskAgainForABodyItCannotParse() {
        db.stubFor(get(urlPathMatching(PROJECTIONS_PATH)).willReturn(okJson("not json at all")));

        assertThatThrownBy(() -> client.listProjections(USER_ID))
                .isInstanceOf(RestClientException.class);
        assertThat(requests()).isEqualTo(1);
    }

    /** Invented, like every value here: enough of a user for the client to map one. */
    private static String user() {
        return """
                {"id":"%s","email":"someone@example.test","username":"someone",\
                "passwordHash":"not-a-hash","tokenVersion":1,"emailVerified":true}\
                """.formatted(USER_ID);
    }

    private static RestClient restClient(WireMockServer server) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(TIMEOUT);
        return RestClient.builder()
                .baseUrl(server.baseUrl())
                .requestFactory(factory)
                .build();
    }

    private int requests() {
        return db.getAllServeEvents().size();
    }
}
