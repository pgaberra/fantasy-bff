package com.fantasy.bff.service;

import com.fantasy.bff.config.LeagueDraftSyncProperties;
import org.springframework.stereotype.Component;

/**
 * Whether this environment lets the draft room follow a Yahoo league's live draft: the answer the
 * draft endpoint enforces and {@code GET /api/v1/features} reports.
 *
 * <p>A followed Yahoo pick names its player by Yahoo's id, so it only lands on the board when the
 * pool is numbered by Yahoo too. A pool on ESPN ids would credit every pick to a player nobody has.
 * An ESPN league's draft is not followed at all: ESPN's league API lists a draft's picks only once
 * the draft is over.
 */
@Component
public class LeagueDraftSyncAvailability {

    private final boolean available;

    public LeagueDraftSyncAvailability(LeagueDraftSyncProperties properties, PlayerPoolSource pool) {
        this.available = properties.enabled() && pool.playerIdSpace() == PlayerIdSpace.YAHOO;
    }

    /** Following a Yahoo league's draft. */
    public boolean available() {
        return available;
    }
}
