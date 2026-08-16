package com.fantasy.bff.service;

import com.fantasy.bff.dto.response.RookiesResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Which players are rookies for the season being projected.
 *
 * <p>Reuses the cached NHL-side context the game-range splits are built on: rookie status rides
 * along on the same player identities, so answering this costs nothing beyond what is already
 * held. It is a decoration on a player list rather than data the app runs on, so a projection
 * service that is unreachable — or switched off, as it is in production — makes the answer
 * unknown rather than making the request fail.
 */
@Service
public class RookieService {

    private static final Logger log = LoggerFactory.getLogger(RookieService.class);

    private final PlayerSplitContextProvider contextProvider;

    public RookieService(PlayerSplitContextProvider contextProvider) {
        this.contextProvider = contextProvider;
    }

    public RookiesResponse rookies() {
        try {
            return contextProvider.context().rookies()
                    .map(RookiesResponse::of)
                    .orElseGet(RookiesResponse::unknown);
        } catch (RuntimeException e) {
            log.error("Could not work out which players are rookies; answering unknown", e);
            return RookiesResponse.unknown();
        }
    }
}
