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
import java.util.Map;

/**
 * Per-client-IP rate limiting for the public auth endpoints: protects against brute-force
 * (login/google), spam registration, and password-reset email flooding. Exceeding a limit
 * returns 429 with a Retry-After header. Disabled or tuned via {@code security.rate-limit}.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Map<String, String> PATH_RULES = Map.of(
            "/api/v1/auth/login", "login",
            "/api/v1/auth/register", "register",
            "/api/v1/auth/google", "google",
            "/api/v1/auth/password/forgot", "password-forgot",
            "/api/v1/auth/password/reset", "password-reset");

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
                || !PATH_RULES.containsKey(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String ruleKey = PATH_RULES.get(request.getRequestURI());
        RateLimitProperties.Rule rule = properties.rules().getOrDefault(ruleKey, properties.fallback());
        String key = ruleKey + ":" + clientIp(request);

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
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
