package com.fantasy.bff.exception;

/**
 * The caller asked for something a premium subscription pays for and does not have one. An
 * expected outcome for a free account rather than a fault, so it is answered and not logged.
 */
public class PremiumRequiredException extends RuntimeException {

    public PremiumRequiredException(String message) {
        super(message);
    }
}
