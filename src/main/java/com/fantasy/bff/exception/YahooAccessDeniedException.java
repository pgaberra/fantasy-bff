package com.fantasy.bff.exception;

/**
 * Yahoo refused the request: yahoo-service answered 403, carrying Yahoo's own sentence. Relayed
 * to the web as its own verdict rather than as "a downstream service is unavailable", which is
 * how an app-level refusal read as our outage from 2026-08-26 until Yahoo restored access.
 */
public class YahooAccessDeniedException extends RuntimeException {

    public YahooAccessDeniedException(String message) {
        super(message);
    }
}
