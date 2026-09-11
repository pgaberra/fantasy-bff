package com.fantasy.bff.exception;

/**
 * A checkout was asked for by an account that already has a live subscription. Refused, because a
 * second checkout would take a second payment for the same Premium. An expected outcome rather
 * than a fault, so it is answered and not logged.
 */
public class SubscriptionAlreadyLiveException extends RuntimeException {

    public SubscriptionAlreadyLiveException(String message) {
        super(message);
    }
}
