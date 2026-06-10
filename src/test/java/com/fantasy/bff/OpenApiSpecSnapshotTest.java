package com.fantasy.bff;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Snapshot test that keeps the committed OpenAPI spec in lockstep with the code.
 *
 * On a normal build it fetches the live spec from the running app and asserts it
 * matches specs/bff-openapi.yaml, so changing a controller/DTO without regenerating
 * the spec fails the build. fantasy-web pins this spec to generate its client.
 * To regenerate after an intentional API change:
 *
 *     ./gradlew test -DupdateSpec=true
 *
 * then commit the updated specs/bff-openapi.yaml.
 *
 * The api-docs endpoints are denied by default, so this test permits them
 * explicitly. The JWT secret comes from {@link BaseIntegrationTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "security.permitted-urls[0]=/api/v1/auth/**",
        "security.permitted-urls[1]=/actuator/health",
        "security.permitted-urls[2]=/v3/api-docs/**",
        "security.permitted-urls[3]=/v3/api-docs.yaml"
})
class OpenApiSpecSnapshotTest extends BaseIntegrationTest {

    @LocalServerPort
    private int port;

    private static final Path SPEC = Path.of("specs", "bff-openapi.yaml");

    @Test
    void committedSpecMatchesGeneratedSpec() throws Exception {
        String generated = normalize(fetchSpec());

        if (Boolean.getBoolean("updateSpec")) {
            Files.createDirectories(SPEC.getParent());
            Files.writeString(SPEC, generated);
            return;
        }

        assertThat(Files.exists(SPEC))
                .as("specs/bff-openapi.yaml is missing â€” run ./gradlew test -DupdateSpec=true to generate it")
                .isTrue();

        String committed = normalize(Files.readString(SPEC));
        assertThat(generated)
                .as("specs/bff-openapi.yaml is stale â€” run ./gradlew test -DupdateSpec=true and commit the result")
                .isEqualTo(committed);
    }

    private String fetchSpec() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs.yaml")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }

    private static String normalize(String raw) {
        return raw.replace("\r\n", "\n").strip() + "\n";
    }
}
