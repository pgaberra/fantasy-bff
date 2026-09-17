package com.fantasy.bff.dto.response;

/**
 * How an unowned player can be picked up. Yahoo and ESPN word this differently; the BFF's own
 * API says it once, so a client does not learn two vocabularies for one fact.
 */
public enum PlayerAvailability {
    FREE_AGENT,
    WAIVERS,
    UNKNOWN
}
