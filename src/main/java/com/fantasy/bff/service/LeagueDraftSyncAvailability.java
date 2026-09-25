package com.fantasy.bff.service;

import com.fantasy.bff.config.LeagueDraftSyncProperties;
import org.springframework.stereotype.Component;

/**
 * Whether this environment lets the draft room follow a league's live draft, per platform: the
 * answers the draft endpoints enforce and {@code GET /api/v1/features} reports.
 *
 * <p>A followed Yahoo pick names its player by Yahoo's id, so it only lands on the board when the
 * pool is numbered by Yahoo too. A pool on ESPN ids would credit every pick to a player nobody has.
 * An ESPN pick carries no such condition: {@link EspnPoolIdCrosswalk} puts it in whichever
 * numbering the pool uses.
 */
@Component
public class LeagueDraftSyncAvailability {

    private final boolean available;
    private final boolean espnAvailable;

    public LeagueDraftSyncAvailability(LeagueDraftSyncProperties properties, PlayerPoolSource pool) {
        this.available = properties.enabled() && pool.playerIdSpace() == PlayerIdSpace.YAHOO;
        this.espnAvailable = properties.espnEnabled();
    }

    /** Following a Yahoo league's draft. */
    public boolean available() {
        return available;
    }

    /** Following an ESPN league's draft. */
    public boolean espnAvailable() {
        return espnAvailable;
    }
}
