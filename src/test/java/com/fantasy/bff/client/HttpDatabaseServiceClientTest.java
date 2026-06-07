package com.fantasy.bff.client;

import com.fantasy.bff.model.downstream.User;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class HttpDatabaseServiceClientTest {

    private WireMockServer server;
    private HttpDatabaseServiceClient client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        RestClient restClient = RestClient.builder().baseUrl(server.baseUrl()).build();
        client = new HttpDatabaseServiceClient(restClient);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void findUserByEmail_returnsUser_on200() {
        server.stubFor(get(urlPathEqualTo("/api/v1/users"))
                .withQueryParam("email", equalTo("a@b.com"))
                .willReturn(okJson("{\"id\":\"u-1\",\"email\":\"a@b.com\",\"passwordHash\":\"hash\"}")));

        Optional<User> result = client.findUserByEmail("a@b.com");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("u-1");
        assertThat(result.get().email()).isEqualTo("a@b.com");
        assertThat(result.get().passwordHash()).isEqualTo("hash");
    }

    @Test
    void findUserByEmail_returnsEmpty_on404() {
        server.stubFor(get(urlPathEqualTo("/api/v1/users"))
                .willReturn(aResponse().withStatus(404)));

        assertThat(client.findUserByEmail("missing@b.com")).isEmpty();
    }

    @Test
    void existsByEmail_returnsBoolean() {
        server.stubFor(get(urlPathEqualTo("/api/v1/users/exists"))
                .withQueryParam("email", equalTo("a@b.com"))
                .willReturn(okJson("{\"exists\":true}")));

        assertThat(client.existsByEmail("a@b.com")).isTrue();
    }

    @Test
    void createUser_postsEmailAndHash() {
        server.stubFor(post(urlPathEqualTo("/api/v1/users"))
                .willReturn(aResponse().withStatus(201)));

        client.createUser("new@b.com", "hashed");

        var requests = server.findAll(postRequestedFor(urlPathEqualTo("/api/v1/users")));
        System.out.println("[DEBUG] createUser POST count=" + requests.size());
        requests.forEach(r -> {
            System.out.println("[DEBUG] content-type=" + r.getHeader("Content-Type"));
            System.out.println("[DEBUG] body=" + r.getBodyAsString());
        });

        server.verify(postRequestedFor(urlPathEqualTo("/api/v1/users"))
                .withRequestBody(equalToJson("{\"email\":\"new@b.com\",\"passwordHash\":\"hashed\"}")));
    }
}
