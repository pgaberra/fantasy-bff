package com.fantasy.bff.service;

import com.fantasy.bff.config.LeagueDraftSyncProperties;
import org.springframework.stereotype.Component;

/**
 * Whether this environment lets the draft room follow a league's live draft: the one answer the
 * draft endpoint enforces and {@code GET /api/v1/features} reports.
 *
 * <p>A followed pick names its player by Yahoo's id, so it only lands on the board when the pool
 * is numbered by Yahoo too. A pool on ESPN ids would credit every pick to a player nobody has.
 */
@Component
public class LeagueDraftSyncAvailability {

    private final boolean available;

    public LeagueDraftSyncAvailability(LeagueDraftSyncProperties properties, PlayerPoolSource pool) {
        this.available = properties.enabled() && pool.playerIdSpace() == PlayerIdSpace.YAHOO;
    }

    public boolean available() {
        return available;
    }
}
