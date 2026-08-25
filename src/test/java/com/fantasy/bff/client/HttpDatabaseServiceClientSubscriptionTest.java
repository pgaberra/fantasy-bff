package com.fantasy.bff.client;

import com.fantasy.bff.generated.db.model.SubscriptionResponse;
import com.fantasy.bff.generated.db.model.UpsertSubscriptionRequest;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class HttpDatabaseServiceClientSubscriptionTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PATH = "/api/v1/users/" + USER_ID + "/subscription";

    private WireMockServer server;
    private HttpDatabaseServiceClient client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        RestClient restClient = RestClient.builder()
                .baseUrl(server.baseUrl())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
        // Both clients point at the same WireMock; only their timeouts differ in production.
        client = new HttpDatabaseServiceClient(restClient, restClient);
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
}
