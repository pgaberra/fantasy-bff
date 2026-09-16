package com.fantasy.bff.security;

import com.fantasy.bff.config.RateLimitProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * Caps how many account emails (verification, password reset) one address can be sent, whoever
 * asks. The per-IP rules in {@link RateLimitFilter} stop one caller; this stops many callers
 * aiming at one inbox, which is the mail-bombing case: register a victim's address, then ask for
 * the link again from as many addresses as it takes.
 *
 * <p>A request over the cap is answered exactly like one that sent mail, so the cap reveals
 * nothing about whether the account exists.
 */
@Component
public class EmailSendThrottle {

    private final RateLimiter rateLimiter;
    private final RateLimitProperties properties;

    public EmailSendThrottle(RateLimiter rateLimiter, RateLimitProperties properties) {
        this.rateLimiter = rateLimiter;
        this.properties = properties;
    }

    /** Records an email about to be sent to {@code address} and reports whether it may go. */
    public boolean tryAcquire(String kind, String address) {
        RateLimitProperties.Rule rule = properties.emailsPerAddress();
        if (!properties.enabled() || rule == null) {
            return true;
        }
        String key = "email:" + kind + ":" + address.trim().toLowerCase(Locale.ROOT);
        return rateLimiter.tryAcquire(key, rule.limit(), Duration.ofSeconds(rule.windowSeconds()));
    }
}
