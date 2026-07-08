package com.fantasy.bff.client;

import com.fantasy.bff.model.downstream.EmailVerificationToken;
import com.fantasy.bff.model.downstream.PasswordResetToken;
import com.fantasy.bff.model.downstream.User;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Instant;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpDatabaseServiceClientTest {

    private WireMockServer server;
    private HttpDatabaseServiceClient client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        // Use a buffered request factory so POST bodies are sent with a
        // Content-Length (not chunked); WireMock then records/matches the body.
        RestClient restClient = RestClient.builder()
                .baseUrl(server.baseUrl())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
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
                .willReturn(okJson("{\"id\":\"u-1\",\"email\":\"a@b.com\",\"passwordHash\":\"hash\",\"tokenVersion\":2,\"emailVerified\":true}")));

        Optional<User> result = client.findUserByEmail("a@b.com");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo("u-1");
        assertThat(result.get().email()).isEqualTo("a@b.com");
        assertThat(result.get().passwordHash()).isEqualTo("hash");
        assertThat(result.get().tokenVersion()).isEqualTo(2);
        assertThat(result.get().emailVerified()).isTrue();
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
    void findOrCreateGoogleUser_postsIdentity_andReturnsResolvedUser() {
        server.stubFor(post(urlPathEqualTo("/api/v1/users/google"))
                .willReturn(okJson("{\"id\":\"u-5\",\"email\":\"g@b.com\",\"passwordHash\":null,\"tokenVersion\":0,\"emailVerified\":true}")));

        User resolved = client.findOrCreateGoogleUser("g@b.com", "google-sub-5");

        assertThat(resolved).isEqualTo(new User("u-5", "g@b.com", null, 0, true));
        server.verify(postRequestedFor(urlPathEqualTo("/api/v1/users/google"))
                .withRequestBody(equalToJson("{\"email\":\"g@b.com\",\"googleSub\":\"google-sub-5\"}")));
    }

    @Test
    void createUser_postsEmailAndHash_andReturnsCreatedUser() {
        server.stubFor(post(urlPathEqualTo("/api/v1/users"))
                .willReturn(okJson("{\"id\":\"u-9\",\"email\":\"new@b.com\",\"passwordHash\":\"hashed\",\"tokenVersion\":0,\"emailVerified\":false}")
                        .withStatus(201)));

        User created = client.createUser("new@b.com", "hashed");

        assertThat(created).isEqualTo(new User("u-9", "new@b.com", "hashed", 0, false));
        server.verify(postRequestedFor(urlPathEqualTo("/api/v1/users"))
                .withRequestBody(equalToJson("{\"email\":\"new@b.com\",\"passwordHash\":\"hashed\"}")));
    }

    @Test
    void createPasswordResetToken_returnsToken_on200() {
        server.stubFor(post(urlPathEqualTo("/api/v1/users/password-reset/tokens"))
                .willReturn(okJson("{\"token\":\"raw-token\",\"expiresAt\":\"2026-06-17T12:00:00Z\"}")));

        Optional<PasswordResetToken> result = client.createPasswordResetToken("a@b.com");

        assertThat(result).isPresent();
        assertThat(result.get().token()).isEqualTo("raw-token");
        assertThat(result.get().expiresAt()).isEqualTo(Instant.parse("2026-06-17T12:00:00Z"));
        server.verify(postRequestedFor(urlPathEqualTo("/api/v1/users/password-reset/tokens"))
                .withRequestBody(equalToJson("{\"email\":\"a@b.com\"}")));
    }

    @Test
    void createPasswordResetToken_returnsEmpty_on204() {
        server.stubFor(post(urlPathEqualTo("/api/v1/users/password-reset/tokens"))
                .willReturn(aResponse().withStatus(204)));

        assertThat(client.createPasswordResetToken("none@b.com")).isEmpty();
    }

    @Test
    void resetPassword_postsTokenAndHash_on204() {
        server.stubFor(post(urlPathEqualTo("/api/v1/users/password-reset"))
                .willReturn(aResponse().withStatus(204)));

        client.resetPassword("raw-token", "hashed");

        server.verify(postRequestedFor(urlPathEqualTo("/api/v1/users/password-reset"))
                .withRequestBody(equalToJson("{\"token\":\"raw-token\",\"passwordHash\":\"hashed\"}")));
    }

    @Test
    void resetPassword_throwsIllegalArgument_on404() {
        server.stubFor(post(urlPathEqualTo("/api/v1/users/password-reset"))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> client.resetPassword("bad", "hashed"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createEmailVerificationToken_returnsToken_on200() {
        server.stubFor(post(urlPathEqualTo("/api/v1/users/email-verification/tokens"))
                .willReturn(okJson("{\"token\":\"raw-token\",\"expiresAt\":\"2026-06-18T12:00:00Z\"}")));

        Optional<EmailVerificationToken> result = client.createEmailVerificationToken("a@b.com");

        assertThat(result).isPresent();
        assertThat(result.get().token()).isEqualTo("raw-token");
        assertThat(result.get().expiresAt()).isEqualTo(Instant.parse("2026-06-18T12:00:00Z"));
        server.verify(postRequestedFor(urlPathEqualTo("/api/v1/users/email-verification/tokens"))
                .withRequestBody(equalToJson("{\"email\":\"a@b.com\"}")));
    }

    @Test
    void createEmailVerificationToken_returnsEmpty_on204() {
        server.stubFor(post(urlPathEqualTo("/api/v1/users/email-verification/tokens"))
                .willReturn(aResponse().withStatus(204)));

        assertThat(client.createEmailVerificationToken("none@b.com")).isEmpty();
    }

    @Test
    void verifyEmail_posts_on204() {
        server.stubFor(post(urlPathEqualTo("/api/v1/users/email-verification"))
                .willReturn(aResponse().withStatus(204)));

        client.verifyEmail("raw-token");

        server.verify(postRequestedFor(urlPathEqualTo("/api/v1/users/email-verification"))
                .withRequestBody(equalToJson("{\"token\":\"raw-token\"}")));
    }

    @Test
    void verifyEmail_throwsIllegalArgument_on404() {
        server.stubFor(post(urlPathEqualTo("/api/v1/users/email-verification"))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> client.verifyEmail("bad"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
