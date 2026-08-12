package com.fantasy.bff.controller;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.generated.db.model.SharedPlayer;
import com.fantasy.bff.generated.db.model.SharedProjectionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.stream.Collectors;

@Tag(name = "Shared projections", description = "Public, read-only projections (no sign-in required)")
@RestController
@RequestMapping("/api/v1/shared")
public class SharedProjectionController {

    private static final int PREVIEW_PLAYERS = 3;

    /**
     * Filled by {@code replace} rather than {@code formatted}: the same value appears in several
     * tags, so named placeholders beat a dozen positional ones — and a format string full of
     * literal newlines is exactly what a bot expects in an HTTP body, not the platform newline
     * {@code %n} would give it.
     */
    private static final String PREVIEW_TEMPLATE = """
            <!doctype html>
            <html lang="en">
              <head>
                <meta charset="utf-8" />
                <title>{{title}}</title>
                <link rel="canonical" href="{{url}}" />
                <meta name="description" content="{{description}}" />
                <meta property="og:type" content="article" />
                <meta property="og:site_name" content="SlapStat" />
                <meta property="og:url" content="{{url}}" />
                <meta property="og:title" content="{{title}}" />
                <meta property="og:description" content="{{description}}" />
                <meta property="og:image" content="{{origin}}/og-image.png" />
                <meta property="og:image:width" content="1200" />
                <meta property="og:image:height" content="630" />
                <meta name="twitter:card" content="summary_large_image" />
                <meta name="twitter:title" content="{{title}}" />
                <meta name="twitter:description" content="{{description}}" />
                <meta name="twitter:image" content="{{origin}}/og-image.png" />
                <meta http-equiv="refresh" content="0; url={{url}}" />
              </head>
              <body><p><a href="{{url}}">View this projection on SlapStat</a></p></body>
            </html>
            """;

    private final DatabaseServiceClient databaseServiceClient;
    private final String webBaseUrl;

    public SharedProjectionController(DatabaseServiceClient databaseServiceClient,
                                      @Value("${app.web-base-url}") String webBaseUrl) {
        this.databaseServiceClient = databaseServiceClient;
        this.webBaseUrl = webBaseUrl;
    }

    @Operation(operationId = "getSharedProjection",
            summary = "Fetch a shared projection by its token",
            description = "Public: anyone holding the link can read it. Returns the snapshot as it "
                    + "was when shared, with no identity beyond the alias the owner chose.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Shared projection found"),
        @ApiResponse(responseCode = "404", description = "No share with that token, or it was taken down")
    })
    @GetMapping("/{token}")
    public SharedProjectionResponse get(@PathVariable String token) {
        return databaseServiceClient.getSharedProjection(token);
    }

    /**
     * The link preview a chat client or social network shows when someone posts a share.
     *
     * <p>Those crawlers run no JavaScript, so the SPA's index.html would give every shared
     * projection the site-wide preview. nginx routes crawler user agents for {@code /s/*} here
     * instead, and humans keep getting the app. Hidden from the OpenAPI spec on purpose: it
     * serves HTML to bots and is not part of the API the web client is generated from.
     */
    @Hidden
    @GetMapping(value = "/{token}/preview", produces = MediaType.TEXT_HTML_VALUE)
    public String preview(@PathVariable String token) {
        SharedProjectionResponse shared = databaseServiceClient.getSharedProjection(token);
        String url = webBaseUrl + "/s/" + token;
        String title = shared.getName() + " — a fantasy hockey projection on SlapStat";
        String description = describe(shared);

        return PREVIEW_TEMPLATE
                .replace("{{title}}", escape(title))
                .replace("{{url}}", escape(url))
                .replace("{{description}}", escape(description))
                .replace("{{origin}}", escape(webBaseUrl));
    }

    private String describe(SharedProjectionResponse shared) {
        String top = shared.getData().getPlayers().stream()
                .limit(PREVIEW_PLAYERS)
                .map(SharedPlayer::getName)
                .collect(Collectors.joining(", "));
        String alias = shared.getAuthorAlias();
        String author = alias == null || alias.isBlank() ? "A SlapStat user" : alias;
        if (top.isBlank()) {
            return author + "'s player rankings for the upcoming NHL season, tuned to their league's "
                    + "scoring settings.";
        }
        return author + "'s player rankings for the upcoming NHL season, tuned to their league's "
                + "scoring settings. Top of the board: " + top + ".";
    }

    /**
     * Everything interpolated into the template is owner-supplied (their projection name, their
     * alias, player names from their rows), so it is escaped rather than trusted — this is the one
     * place in the BFF that builds HTML.
     *
     * <p>The braces are neutralised too: htmlEscape leaves them alone, so a projection named
     * {@code {{url}}} would otherwise be substituted by a later replacement pass.
     */
    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value == null ? "" : value).replace("{{", "&#123;&#123;");
    }
}
