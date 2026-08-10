package com.fantasy.bff.security;

import com.fantasy.bff.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Per-client-IP rate limiting for the public endpoints: brute-force (login/google), spam
 * registration, password-reset email flooding, and the public share reads, where the card render
 * is real work anyone with a link can ask for. Exceeding a limit returns 429 with a Retry-After
 * header. The protected endpoints and their limits are fully configured under
 * {@code security.rate-limit.endpoints} — no paths are hard-coded here.
 *
 * <p>A key may be an exact path or an Ant pattern ({@code /api/v1/shared/*&#47;preview}). Patterns
 * count every matching path into <em>one</em> bucket per client, which is the point for the share
 * reads: a caller working through a list of tokens hits one path each time, so per-path buckets
 * would never fill.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String RATE_LIMITED_BODY =
            "{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests. Please try again later.\",\"details\":{}}";

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;

    public RateLimitFilter(RateLimiter rateLimiter, RateLimitProperties properties) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled() || matchingRule(request).isEmpty();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        Map.Entry<String, RateLimitProperties.Rule> matched = matchingRule(request).orElseThrow();
        RateLimitProperties.Rule rule = matched.getValue();
        // Keyed on the configured pattern, not the request path, so every path a pattern covers
        // shares one bucket per client.
        String key = matched.getKey() + ":" + clientIp(request);

        if (rateLimiter.tryAcquire(key, rule.limit(), Duration.ofSeconds(rule.windowSeconds()))) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(rule.windowSeconds()));
        response.getWriter().write(RATE_LIMITED_BODY);
    }

    /**
     * The rule guarding this request, if any. An exact path wins outright; otherwise the most
     * specific matching pattern does, so a rule for one path can sit alongside a broader one
     * without the broader one swallowing it.
     */
    private Optional<Map.Entry<String, RateLimitProperties.Rule>> matchingRule(HttpServletRequest request) {
        String path = request.getRequestURI();
        RateLimitProperties.Rule exact = properties.endpoints().get(path);
        if (exact != null) {
            return methodMatches(request, exact)
                    ? Optional.of(Map.entry(path, exact))
                    : Optional.empty();
        }
        return properties.endpoints().entrySet().stream()
                .filter(entry -> methodMatches(request, entry.getValue()))
                .filter(entry -> PATH_MATCHER.isPattern(entry.getKey()))
                .filter(entry -> PATH_MATCHER.match(entry.getKey(), path))
                .min((first, second) ->
                        PATH_MATCHER.getPatternComparator(path).compare(first.getKey(), second.getKey()));
    }

    private boolean methodMatches(HttpServletRequest request, RateLimitProperties.Rule rule) {
        return rule.methodOrDefault().matches(request.getMethod());
    }

    private String clientIp(HttpServletRequest request) {
        // Behind our single trusted proxy (Traefik) the real client IP is the LAST entry of
        // X-Forwarded-For — the one Traefik appends from the actual TCP peer. A client can prepend
        // spoofed entries but cannot forge that last hop, so keying on the first entry (as before)
        // let an attacker rotate it to get a fresh bucket per request and dodge the limit entirely.
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            String[] hops = forwarded.split(",");
            return hops[hops.length - 1].trim();
        }
        return request.getRemoteAddr();
    }
}
