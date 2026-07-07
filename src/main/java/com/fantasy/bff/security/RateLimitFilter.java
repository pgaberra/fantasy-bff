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
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Per-client-IP rate limiting for the public auth endpoints: protects against brute-force
 * (login/google), spam registration, and password-reset email flooding. Exceeding a limit
 * returns 429 with a Retry-After header. The protected endpoints and their limits are fully
 * configured under {@code security.rate-limit.endpoints} — no paths are hard-coded here.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String RATE_LIMITED_BODY =
            "{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests. Please try again later.\",\"details\":{}}";

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;

    public RateLimitFilter(RateLimiter rateLimiter, RateLimitProperties properties) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !properties.enabled()
                || !HttpMethod.POST.matches(request.getMethod())
                || !properties.endpoints().containsKey(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        RateLimitProperties.Rule rule = properties.endpoints().get(path);
        String key = path + ":" + clientIp(request);

        if (rateLimiter.tryAcquire(key, rule.limit(), Duration.ofSeconds(rule.windowSeconds()))) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(rule.windowSeconds()));
        response.getWriter().write(RATE_LIMITED_BODY);
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
