package com.fantasy.bff.client;

import com.fantasy.bff.generated.db.model.ImportProjectionRequest;
import com.fantasy.bff.generated.db.model.PlayerIdPair;
import com.fantasy.bff.generated.db.model.PlayerIdRemapResponse;
import com.fantasy.bff.generated.db.model.ProjectionResponse;
import com.fantasy.bff.model.downstream.Avatar;
import com.fantasy.bff.model.downstream.EmailVerificationToken;
import com.fantasy.bff.model.downstream.PasswordResetToken;
import com.fantasy.bff.model.downstream.User;
import com.fantasy.bff.support.WireMockConfigs;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpDatabaseServiceClientTest {

    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

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
    void findAvatar_decodesTheBytes_on200() {
        server.stubFor(get(urlPathEqualTo("/api/v1/users/" + USER_ID + "/avatar"))
                .willReturn(okJson("{\"contentType\":\"image/png\",\"data\":\"AQID\",\"updatedAt\":\"2026-09-03T12:00:00Z\"}")));

        Optional<Avatar> result = client.findAvatar(UUID.fromString(USER_ID));

        assertThat(result).isPresent();
        assertThat(result.get().contentType()).isEqualTo("image/png");
        assertThat(result.get().data()).isEqualTo(new byte[]{1, 2, 3});
    }

    @Test
    void findAvatar_returnsEmpty_on404() {
        server.stubFor(get(urlPathEqualTo("/api/v1/users/" + USER_ID + "/avatar"))
                .willReturn(aResponse().withStatus(404)));

        assertThat(client.findAvatar(UUID.fromString(USER_ID))).isEmpty();
    }

    @Test
    void findSharedProjectionAuthorAvatar_decodesTheBytes_on200() {
        server.stubFor(get(urlPathEqualTo("/api/v1/shares/share-token/avatar"))
                .willReturn(okJson("{\"contentType\":\"image/png\",\"data\":\"AQID\",\"updatedAt\":\"2026-09-03T12:00:00Z\"}")));

        Optional<Avatar> result = client.findSharedProjectionAuthorAvatar("share-token");

        assertThat(result).isPresent();
        assertThat(result.get().contentType()).isEqualTo("image/png");
        assertThat(result.get().data()).isEqualTo(new byte[]{1, 2, 3});
    }

    @Test
    void findSharedProjectionAuthorAvatar_returnsEmpty_on404() {
        server.stubFor(get(urlPathEqualTo("/api/v1/shares/share-token/avatar"))
                .willReturn(aResponse().withStatus(404)));

        assertThat(client.findSharedProjectionAuthorAvatar("share-token")).isEmpty();
    }

    @Test
    void setAvatar_putsTheBytesAsBase64WithTheirType() {
        server.stubFor(put(urlPathEqualTo("/api/v1/users/" + USER_ID + "/avatar"))
                .willReturn(okJson("{\"contentType\":\"image/png\",\"data\":\"AQID\",\"updatedAt\":\"2026-09-03T12:00:00Z\"}")));

        client.setAvatar(UUID.fromString(USER_ID), new Avatar("image/png", new byte[]{1, 2, 3}));

        server.verify(putRequestedFor(urlPathEqualTo("/api/v1/users/" + USER_ID + "/avatar"))
                .withRequestBody(equalToJson("{\"contentType\":\"image/png\",\"data\":\"AQID\"}")));
    }

    @Test
    void deleteAvatar_tolerates404_sinceRemovingNothingIsNotAnError() {
        server.stubFor(delete(urlPathEqualTo("/api/v1/users/" + USER_ID + "/avatar"))
                .willReturn(aResponse().withStatus(404)));

        client.deleteAvatar(UUID.fromString(USER_ID));

        server.verify(deleteRequestedFor(urlPathEqualTo("/api/v1/users/" + USER_ID + "/avatar")));
    }

    @Test
    void deleteAvatar_relaysOtherFailures() {
        server.stubFor(delete(urlPathEqualTo("/api/v1/users/" + USER_ID + "/avatar"))
                .willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.deleteAvatar(UUID.fromString(USER_ID)))
                .isInstanceOf(org.springframework.web.client.RestClientResponseException.class);
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

        assertThat(resolved).isEqualTo(new User("u-5", "g@b.com", null, null, 0, true));
        server.verify(postRequestedFor(urlPathEqualTo("/api/v1/users/google"))
                .withRequestBody(equalToJson("{\"email\":\"g@b.com\",\"googleSub\":\"google-sub-5\"}")));
    }

    @Test
    void createUser_postsEmailAndHash_andReturnsCreatedUser() {
        server.stubFor(post(urlPathEqualTo("/api/v1/users"))
                .willReturn(okJson("{\"id\":\"u-9\",\"email\":\"new@b.com\",\"passwordHash\":\"hashed\",\"tokenVersion\":0,\"emailVerified\":false}")
                        .withStatus(201)));

        User created = client.createUser("new@b.com", "hashed");

        assertThat(created).isEqualTo(new User("u-9", "new@b.com", null, "hashed", 0, false));
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

    @Test
    void importProjection_postsTheTokenToTheImportsCollection() {
        server.stubFor(post(urlPathEqualTo("/api/v1/users/" + USER_ID + "/projections/imports"))
                .willReturn(okJson("{\"id\":\"p-1\",\"name\":\"Their league\",\"kind\":\"imported\","
                        + "\"season\":\"20262027\",\"createdAt\":\"2026-08-01T10:00:00Z\","
                        + "\"updatedAt\":\"2026-08-01T10:00:00Z\","
                        + "\"origin\":{\"shareToken\":\"t0k3n\",\"authorUsername\":\"alex\"}}")));

        ProjectionResponse imported = client.importProjection(
                UUID.fromString(USER_ID), new ImportProjectionRequest().token("t0k3n"));

        assertThat(imported.getKind()).isEqualTo(ProjectionResponse.KindEnum.IMPORTED);
        assertThat(imported.getOrigin().getAuthorUsername()).isEqualTo("alex");
        server.verify(postRequestedFor(
                        urlPathEqualTo("/api/v1/users/" + USER_ID + "/projections/imports"))
                .withRequestBody(equalToJson("{\"token\":\"t0k3n\"}", true, true)));
    }

    @Test
    void remapPlayerIds_postsTheCrosswalkAndTheDryRunFlag() {
        server.stubFor(post(urlPathEqualTo("/api/v1/admin/player-ids/remap"))
                .willReturn(okJson("{\"dryRun\":true,\"projectionsScanned\":110,"
                        + "\"playerRows\":{\"remapped\":136990,\"unmapped\":26677,\"colliding\":3},"
                        + "\"draftPicks\":{\"remapped\":1676,\"unmapped\":0,\"colliding\":0},"
                        + "\"sharesScanned\":1,"
                        + "\"sharedRows\":{\"remapped\":100,\"unmapped\":0,\"colliding\":0},"
                        + "\"unmappedPlayerIds\":[3988],"
                        + "\"collidingPlayerIds\":[5714,5738,5767]}")));

        PlayerIdRemapResponse response = client.remapPlayerIds(
                List.of(new PlayerIdPair().from(6743).to(3895074)), true,
                com.fantasy.bff.service.PlayerIdSpace.YAHOO, com.fantasy.bff.service.PlayerIdSpace.ESPN);

        assertThat(response.getProjectionsScanned()).isEqualTo(110);
        assertThat(response.getDraftPicks().getUnmapped()).isZero();
        assertThat(response.getPlayerRows().getColliding()).isEqualTo(3);
        assertThat(response.getCollidingPlayerIds()).containsExactly(5714, 5738, 5767);
        server.verify(postRequestedFor(urlPathEqualTo("/api/v1/admin/player-ids/remap"))
                .withRequestBody(equalToJson(
                        "{\"mappings\":[{\"from\":6743,\"to\":3895074}],\"dryRun\":true,"
                                + "\"from\":\"yahoo\",\"to\":\"espn\"}",
                        true, true)));
    }

    @Test
    void revokeSessions_postsToTheUsersSessions() {
        server.stubFor(post(urlPathEqualTo("/api/v1/users/" + USER_ID + "/sessions/revoke"))
                .willReturn(aResponse().withStatus(204)));

        client.revokeSessions(UUID.fromString(USER_ID));

        server.verify(postRequestedFor(urlPathEqualTo("/api/v1/users/" + USER_ID + "/sessions/revoke")));
    }

    @Test
    void revokeSessions_throwsWhenTheUserIsUnknown() {
        server.stubFor(post(urlPathEqualTo("/api/v1/users/" + USER_ID + "/sessions/revoke"))
                .willReturn(aResponse().withStatus(404)));

        assertThatThrownBy(() -> client.revokeSessions(UUID.fromString(USER_ID)))
                .isInstanceOf(RuntimeException.class);
    }
}
