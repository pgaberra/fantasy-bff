package com.fantasy.bff.client;

import com.fantasy.bff.generated.db.model.PendingCheckoutResponse;
import com.fantasy.bff.generated.db.model.ReplacePendingCheckoutRequest;
import com.fantasy.bff.generated.db.model.SubscriptionResponse;
import com.fantasy.bff.generated.db.model.UpsertSubscriptionRequest;
import com.fantasy.bff.support.WireMockConfigs;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

class HttpDatabaseServiceClientSubscriptionTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PATH = "/api/v1/users/" + USER_ID + "/subscription";

    private WireMockServer server;
    private HttpDatabaseServiceClient client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(WireMockConfigs.http11());
        server.start();
        RestClient restClient = RestClient.builder()
                .baseUrl(server.baseUrl())
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();
        // All three clients point at the same WireMock; only their timeouts differ in production.
        client = new HttpDatabaseServiceClient(restClient, restClient, restClient);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void getSubscription_returnsSubscription_on200() {
        server.stubFor(get(urlPathEqualTo(PATH)).willReturn(okJson(
                "{\"status\":\"active\",\"cancelAtPeriodEnd\":false,\"premium\":true,"
                        + "\"provider\":\"mock\",\"providerCustomerId\":\"cus_1\"}")));

        Optional<SubscriptionResponse> result = client.getSubscription(USER_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getPremium()).isTrue();
        assertThat(result.get().getStatus()).isEqualTo(SubscriptionResponse.StatusEnum.ACTIVE);
    }

    @Test
    void getSubscription_returnsEmpty_on404() {
        server.stubFor(get(urlPathEqualTo(PATH)).willReturn(aResponse().withStatus(404)));

        assertThat(client.getSubscription(USER_ID)).isEmpty();
    }

    @Test
    void upsertSubscription_putsRequest_andReturnsSubscription() {
        server.stubFor(put(urlPathEqualTo(PATH)).willReturn(okJson(
                "{\"status\":\"active\",\"cancelAtPeriodEnd\":false,\"premium\":true,\"provider\":\"mock\"}")));

        UpsertSubscriptionRequest request = new UpsertSubscriptionRequest()
                .provider("mock")
                .status(UpsertSubscriptionRequest.StatusEnum.ACTIVE)
                .cancelAtPeriodEnd(false)
                .eventAt(OffsetDateTime.now(ZoneOffset.UTC));

        SubscriptionResponse response = client.upsertSubscription(USER_ID, request);

        assertThat(response.getPremium()).isTrue();
        server.verify(putRequestedFor(urlPathEqualTo(PATH))
                .withRequestBody(equalToJson("{\"provider\":\"mock\",\"status\":\"active\"}", true, true)));
    }

    private static final String PENDING_PATH = "/api/v1/users/" + USER_ID + "/pending-checkout";
    private static final String PENDING_BODY = "{\"provider\":\"paddle\",\"reference\":\"txn_1\","
            + "\"checkoutUrl\":\"https://slapstat.test/pay?_ptxn=txn_1\",\"updatedAt\":\"2026-09-11T19:00:00Z\"}";

    @Test
    void getPendingCheckout_returnsCheckout_on200() {
        server.stubFor(get(urlPathEqualTo(PENDING_PATH)).willReturn(okJson(PENDING_BODY)));

        Optional<PendingCheckoutResponse> result = client.getPendingCheckout(USER_ID);

        assertThat(result).isPresent();
        assertThat(result.get().getReference()).isEqualTo("txn_1");
        assertThat(result.get().getCheckoutUrl()).isEqualTo("https://slapstat.test/pay?_ptxn=txn_1");
    }

    @Test
    void getPendingCheckout_returnsEmpty_on404() {
        server.stubFor(get(urlPathEqualTo(PENDING_PATH)).willReturn(aResponse().withStatus(404)));

        assertThat(client.getPendingCheckout(USER_ID)).isEmpty();
    }

    @Test
    void replacePendingCheckout_returnsTrue_whenStored() {
        server.stubFor(put(urlPathEqualTo(PENDING_PATH)).willReturn(okJson(PENDING_BODY)));

        boolean stored = client.replacePendingCheckout(USER_ID, new ReplacePendingCheckoutRequest()
                .provider("paddle").reference("txn_2").checkoutUrl("https://slapstat.test/pay?_ptxn=txn_2")
                .replacesReference("txn_1"));

        assertThat(stored).isTrue();
        server.verify(putRequestedFor(urlPathEqualTo(PENDING_PATH))
                .withRequestBody(equalToJson("{\"reference\":\"txn_2\",\"replacesReference\":\"txn_1\"}", true, true)));
    }

    /** A lost compare-and-set is an answer the caller acts on, not a failure to relay. */
    @Test
    void replacePendingCheckout_returnsFalse_on409() {
        server.stubFor(put(urlPathEqualTo(PENDING_PATH)).willReturn(aResponse().withStatus(409)));

        boolean stored = client.replacePendingCheckout(USER_ID, new ReplacePendingCheckoutRequest()
                .provider("paddle").reference("txn_2").checkoutUrl("https://slapstat.test/pay?_ptxn=txn_2"));

        assertThat(stored).isFalse();
    }
}
