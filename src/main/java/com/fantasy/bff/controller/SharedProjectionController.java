package com.fantasy.bff.controller;

import com.fantasy.bff.client.DatabaseServiceClient;
import com.fantasy.bff.config.PlayerAvatarsProperties;
import com.fantasy.bff.service.RookieService;
import com.fantasy.bff.service.ShareCardRenderer;
import com.fantasy.bff.dto.response.SharedProjectionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Tag(name = "Shared projections", description = "Public, read-only projections (no sign-in required)")
@RestController
@RequestMapping("/api/v1/shared")
public class SharedProjectionController {

    /**
     * How many players the link preview names. Surnames alone, and a description that no longer
     * explains the scoring settings, leave room for eight where three full names filled the line.
     */
    private static final int PREVIEW_PLAYERS = 8;

    /**
     * Filled by {@code replace} rather than {@code formatted}: the same value appears in several
     * tags, so named placeholders beat a dozen positional ones — and a format string full of
     * literal newlines is exactly what a bot expects in an HTTP body, not the platform newline
     * {@code %n} would give it.
     *
     * <p>{@code noindex} and no canonical link: a share is for the people its author sends the
     * link to, and the share dialog never says the page could turn up in search. Search crawlers
     * are routed here like the preview bots (nginx matches Googlebot, Bingbot and Applebot too),
     * so this document is what keeps a share's name and its author's username out of results.
     * Preview bots ignore the tag, so links still unfurl.
     */
    private static final String PREVIEW_TEMPLATE = """
            <!doctype html>
            <html lang="en">
              <head>
                <meta charset="utf-8" />
                <meta name="robots" content="noindex" />
                <title>{{title}}</title>
                <meta name="description" content="{{description}}" />
                <meta property="og:type" content="article" />
                <meta property="og:site_name" content="SlapStat" />
                <meta property="og:url" content="{{url}}" />
                <meta property="og:title" content="{{title}}" />
                <meta property="og:description" content="{{description}}" />
                <meta property="og:image" content="{{origin}}/s/{{token}}/og-image.png" />
                <meta property="og:image:width" content="1200" />
                <meta property="og:image:height" content="630" />
                <meta name="twitter:card" content="summary_large_image" />
                <meta name="twitter:title" content="{{title}}" />
                <meta name="twitter:description" content="{{description}}" />
                <meta name="twitter:image" content="{{origin}}/s/{{token}}/og-image.png" />
                <meta http-equiv="refresh" content="0; url={{url}}" />
              </head>
              <body><p><a href="{{url}}">View this projection on SlapStat</a></p></body>
            </html>
            """;

    private final DatabaseServiceClient databaseServiceClient;
    private final ShareCardRenderer shareCardRenderer;
    private final RookieService rookieService;
    private final PlayerAvatarsProperties avatars;
    private final String webBaseUrl;

    public SharedProjectionController(DatabaseServiceClient databaseServiceClient,
                                      ShareCardRenderer shareCardRenderer,
                                      RookieService rookieService,
                                      PlayerAvatarsProperties avatars,
                                      @Value("${app.web-base-url}") String webBaseUrl) {
        this.databaseServiceClient = databaseServiceClient;
        this.shareCardRenderer = shareCardRenderer;
        this.rookieService = rookieService;
        this.avatars = avatars;
        this.webBaseUrl = webBaseUrl;
    }

    /**
     * The page behind a share link. Unauthenticated on purpose — a link has to open for someone
     * who has never signed in — and the same whole board for everyone who opens it: the page
     * sorts, filters and searches it in the browser.
     */
    @Operation(operationId = "getSharedProjection",
            summary = "Fetch a shared projection by its token",
            description = "Public: anyone holding the link reads the whole published board, "
                    + "signed in or not. Returns the snapshot as it was when shared, with no "
                    + "identity beyond the owner's public username.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Shared projection found"),
        @ApiResponse(responseCode = "404", description = "No share with that token, or it was taken down")
    })
    @GetMapping("/{token}")
    public SharedProjectionResponse get(@PathVariable String token) {
        return SharedProjectionResponse.of(
                databaseServiceClient.getSharedProjection(token), rookieIds(), avatars.enabled());
    }

    /**
     * The author's profile picture, for the byline on the public page. Unauthenticated like the
     * page itself, and fetched by the share token, so neither the browser nor this service learns
     * whose account is behind the link.
     *
     * <p>A picture the author removed between the page loading and the browser asking for it is a
     * 404 rather than an error: the page draws their initials instead, as it does for everyone who
     * never uploaded one.
     */
    @Operation(operationId = "getSharedProjectionAuthorAvatar",
            summary = "Fetch the profile picture of whoever shared a projection",
            description = "Public, like the page it is drawn on. `authorAvatar` on the shared "
                    + "projection is the address to ask at, and is absent where the author has "
                    + "no picture.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Picture returned"),
        @ApiResponse(responseCode = "404", description = "No share with that token, or its author has no picture")
    })
    @GetMapping(value = "/{token}/avatar", produces = "image/*")
    public ResponseEntity<byte[]> authorAvatar(@PathVariable String token) {
        return databaseServiceClient.findSharedProjectionAuthorAvatar(token)
                .map(avatar -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(avatar.contentType()))
                        // The address carries the picture's stamp, so a cached copy can only be
                        // stale for as long as it takes a replaced picture to reach a reader who
                        // already has the page open.
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                        // Kept out of image search for the reason the card below is: it is a
                        // person's face, published to whoever holds a link and no further.
                        .header("X-Robots-Tag", "noindex")
                        .body(avatar.data()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Who is a rookie, or null where nobody can say — which is any environment without the
     * projection service, production included. Null rather than an empty set so the two stay
     * apart: an empty set would narrow a board to nothing when the filter is on, and would tell
     * the page that nobody is a rookie rather than that there is no answer.
     */
    private Set<Integer> rookieIds() {
        var rookies = rookieService.rookies();
        return rookies.known() ? Set.copyOf(rookies.playerIds()) : null;
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
        com.fantasy.bff.generated.db.model.SharedProjectionResponse shared =
                databaseServiceClient.getSharedProjection(token);
        String url = webBaseUrl + "/s/" + token;
        String title = shared.getName() + " by " + shared.getAuthorUsername()
                + " - Fantasy Hockey Projections | SlapStat";
        String description = describe(shared);

        return PREVIEW_TEMPLATE
                .replace("{{title}}", escape(title))
                .replace("{{url}}", escape(url))
                .replace("{{description}}", escape(description))
                .replace("{{origin}}", escape(webBaseUrl))
                .replace("{{token}}", escape(token));
    }

    /**
     * The picture the preview above points at: the projection's name, who made it, and the top of
     * their board, drawn from the same snapshot the page shows.
     *
     * <p>Reached through the web origin (`/s/{token}/og-image.png`, proxied by nginx) so the tags
     * and the image share a host and no extra environment config is needed. Hidden from the spec
     * for the same reason as the preview: it is for crawlers, not for the web client.
     */
    @Hidden
    @GetMapping(value = "/{token}/og-image.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> ogImage(@PathVariable String token) {
        byte[] card = shareCardRenderer.render(databaseServiceClient.getSharedProjection(token));
        return ResponseEntity.ok()
                // Crawlers refetch this far more often than the snapshot changes, and a stale card
                // for an hour after a re-share is a better trade than rendering on every hit.
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                // Kept out of image search for the same reason the preview is noindex: the card
                // names the projection and its author. A PNG has no meta tag, so a header it is.
                .header("X-Robots-Tag", "noindex")
                .contentType(MediaType.IMAGE_PNG)
                .body(card);
    }

    private String describe(com.fantasy.bff.generated.db.model.SharedProjectionResponse shared) {
        String top = shared.getData().getPlayers().stream()
                .limit(PREVIEW_PLAYERS)
                .map(player -> surname(player.getName()))
                .collect(Collectors.joining(", "));
        String author = shared.getAuthorUsername();
        if (top.isBlank()) {
            return author + "'s NHL projections for the upcoming season.";
        }
        return author + "'s NHL projections for the upcoming season. Top players: " + top + ".";
    }

    /**
     * "Connor McDavid" reads as "McDavid" in the preview. The given name costs characters an unfurl
     * does not have and tells the reader nothing they cannot supply themselves.
     */
    private static String surname(String name) {
        String trimmed = name == null ? "" : name.trim();
        int lastSpace = trimmed.lastIndexOf(' ');
        return lastSpace < 0 ? trimmed : trimmed.substring(lastSpace + 1);
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
