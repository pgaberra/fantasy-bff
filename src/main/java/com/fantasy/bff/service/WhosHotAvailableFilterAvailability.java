package com.fantasy.bff.service;

import com.fantasy.bff.config.WhosHotAvailableFilterProperties;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Component;

/**
 * Whether this environment lets Who's hot show only the players a league has available: the one
 * answer the rostered-players endpoint enforces and {@code GET /api/v1/features} reports.
 *
 * <p>The board names its players by the pool's ids, and a Yahoo roster names them by Yahoo's, so
 * the two only meet over a pool numbered by Yahoo — the same condition the draft room's follow
 * has. An ESPN roster is translated into the pool's numbering either way.
 */
@Component
public class WhosHotAvailableFilterAvailability {

    private final boolean available;

    public WhosHotAvailableFilterAvailability(
            WhosHotAvailableFilterProperties properties, PlayerPoolSource pool) {
        this.available = properties.enabled() && pool.playerIdSpace() == PlayerIdSpace.YAHOO;
    }

    public boolean available() {
        return available;
    }

    /** Refuses with a 404, so an environment without the filter has no route for it at all. */
    public void require() {
        if (!available) {
            throw new NoSuchElementException("The available-players filter is not available");
        }
    }
}
