package com.fantasy.bff.client;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import com.fantasy.bff.generated.projection.model.SkaterProjectionResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

class HttpProjectionServiceClientTest {

    private WireMockServer server;
    private HttpProjectionServiceClient client;

    @BeforeEach
    void setUp() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        RestClient restClient = RestClient.builder()
                .baseUrl(server.baseUrl())
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
        client = new HttpProjectionServiceClient(restClient);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void skaterProjections_parsesResponseAndSendsQueryParams() {
        server.stubFor(get(urlPathEqualTo("/api/v1/projections/skaters"))
                .withQueryParam("season", equalTo("2026"))
                .withQueryParam("model_version", equalTo("marcel-v1"))
                .willReturn(okJson("""
                        [{"nhl_id":8478402,"target_season":2026,"model_version":"marcel-v1",
                          "goals":48.5,"assists":90.1,"points":138.6,"shots":306.0,"shooting_pct":0.158}]
                        """)));

        List<SkaterProjectionResponse> projections = client.skaterProjections(2026, "marcel-v1");

        assertThat(projections).hasSize(1);
        SkaterProjectionResponse mcdavid = projections.getFirst();
        assertThat(mcdavid.getModelVersion()).isEqualTo("marcel-v1");
        // openapi-generator maps `type: number` (no format) to BigDecimal.
        assertThat(mcdavid.getPoints()).isEqualByComparingTo(new BigDecimal("138.6"));
        assertThat(mcdavid.getShootingPct()).isEqualByComparingTo(new BigDecimal("0.158"));
    }
}
